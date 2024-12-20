/*! ******************************************************************************
 *
 * Pentaho
 *
 * Copyright (C) 2024 by Hitachi Vantara, LLC : http://www.pentaho.com
 *
 * Use of this software is governed by the Business Source License included
 * in the LICENSE.TXT file.
 *
 * Change Date: 2029-07-20
 ******************************************************************************/


package org.pentaho.mantle.client.solutionbrowser.fileproperties;

import java.util.ArrayList;

import org.pentaho.gwt.widgets.client.dialogs.IDialogCallback;
import org.pentaho.gwt.widgets.client.dialogs.MessageDialogBox;
import org.pentaho.gwt.widgets.client.dialogs.PromptDialogBox;
import org.pentaho.gwt.widgets.client.filechooser.RepositoryFile;
import org.pentaho.gwt.widgets.client.tabs.PentahoTab;
import org.pentaho.gwt.widgets.client.tabs.PentahoTabPanel;
import org.pentaho.mantle.client.dialogs.WaitPopup;
import org.pentaho.mantle.client.events.EventBusUtil;
import org.pentaho.mantle.client.events.GenericEvent;
import org.pentaho.mantle.client.messages.Messages;
import org.pentaho.mantle.client.solutionbrowser.SolutionBrowserPanel;
import org.pentaho.mantle.client.ui.PerspectiveManager;

import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.user.client.ui.Widget;

/**
 * File properties parent panel displayed when right clicking a file in PUC repo browser. Subpanels include: General,
 * Share, History
 */
public class FilePropertiesDialog extends PromptDialogBox {
  public enum Tabs {
    GENERAL, PERMISSION
  }

  private PentahoTabPanel propertyTabs;
  private GeneralPanel generalTab;
  private PermissionsPanel permissionsTab;

  private String moduleBaseURL = GWT.getModuleBaseURL();
  private String moduleName = GWT.getModuleName();
  private String contextURL = moduleBaseURL.substring( 0, moduleBaseURL.lastIndexOf( moduleName ) );
  boolean canManageAcls = false;
  boolean dirty = false;

  private String parentPath = null;

  /**
   * @param fileSummary
   * @param propertyTabs
   * @param callback
   * @param defaultTab
   */
  public FilePropertiesDialog( RepositoryFile fileSummary, final PentahoTabPanel propertyTabs,
      final IDialogCallback callback, Tabs defaultTab, final boolean canManageAcls ) {
    super(
        fileSummary.getTitle() + " " + Messages.getString( "properties" ), Messages.getString( "ok" ), Messages.getString( "cancel" ), false, true ); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    boolean isInTrash = fileSummary.getPath().contains( "/.trash/pho:" );
    setContent( propertyTabs );
    this.canManageAcls = canManageAcls;
    generalTab = new GeneralPanel( this, fileSummary );

    if ( canManageAcls && !isInTrash ) {
      permissionsTab = new PermissionsPanel( fileSummary );
    }

    generalTab.getElement().setId( "filePropertiesGeneralTab" );
    if ( canManageAcls && !isInTrash ) {
      permissionsTab.getElement().setId( "filePropertiesPermissionsTab" );
    }

    // get metadata via REST
    getMetadata( fileSummary );
    getAcls( fileSummary );

    okButton.getElement().setId( "filePropertiesOKButton" );
    cancelButton.getElement().setId( "filePropertiesCancelButton" );

    if ( fileSummary.isFolder() ) {
      parentPath = fileSummary.getPath();
    } else {
      parentPath = fileSummary.getPath().substring(0, fileSummary.getPath().lastIndexOf("/"));
    }

    super.setCallback( new IDialogCallback() {

      public void cancelPressed() {
        if ( callback != null ) {
          callback.cancelPressed();
        }
      }

      public void okPressed() {
        applyPanel();
        if ( callback != null ) {
          callback.okPressed();
        }
      }
    } );
    this.propertyTabs = propertyTabs;
    this.propertyTabs.addTab( Messages.getString( "general" ), Messages.getString( "general" ), false, generalTab );
    if ( canManageAcls && permissionsTab != null ) {
      this.propertyTabs.addTab( Messages.getString( "share" ), Messages.getString( "share" ), false, permissionsTab );
    }
    getWidget().setHeight( "100%" ); //$NON-NLS-1$
    getWidget().setWidth( "100%" ); //$NON-NLS-1$
    setPixelSize( 490, 420 );
    setResponsive( true );
    setWidthCategory( DialogWidthCategory.SMALL );
    setSizingMode( DialogSizingMode.FILL_VIEWPORT_WIDTH );
    addStyleName( "file-properties-dialog" );
    showTab( defaultTab );
  }

  /**
   *
   */
  private void applyPanel() {
    ArrayList<RequestBuilder> requestBuilders = new ArrayList<RequestBuilder>();
    for ( int i = 0; i < propertyTabs.getTabCount(); i++ ) {
      Widget w = propertyTabs.getTab( i ).getContent();
      if ( w instanceof IFileModifier ) {
        // get requests from sub panels
        if ( ( (IFileModifier) w ).prepareRequests() != null ) {
          requestBuilders.addAll( ( (IFileModifier) w ).prepareRequests() );
        }
      }
    }

    RequestCallback requestCallback;
    // chain requests from subpanels using callbacks to try and avoid any StaleItemStateExceptions
    for ( int i = 0; i <= requestBuilders.size() - 1; i++ ) {
      RequestBuilder requestBuilder = requestBuilders.get( i );
      if ( i < requestBuilders.size() - 1 ) {
        final RequestBuilder nextRequest = requestBuilders.get( i + 1 );
        // This header is required to force Internet Explorer to not cache values from the GET response.
        nextRequest.setHeader( "If-Modified-Since", "01 Jan 1970 00:00:00 GMT" );
        requestCallback = new ChainedRequestCallback( nextRequest );
      } else {
        requestCallback = new RequestCallback() {
          @Override
          public void onError( Request request, Throwable th ) {
            WaitPopup.getInstance().setVisible( false );
            MessageDialogBox dialogBox =
                new MessageDialogBox( Messages.getString( "error" ), th.toString(), false, false, true ); //$NON-NLS-1$
            dialogBox.center();
          }

          @Override
          public void onResponseReceived( Request arg0, Response arg1 ) {
            WaitPopup.getInstance().setVisible( false );
            if ( arg1.getStatusCode() == Response.SC_OK ) {
              dirty = false;
              // Refresh current folder or parent folder
              PerspectiveManager.getInstance().setPerspective( PerspectiveManager.BROWSER_PERSPECTIVE );

              GenericEvent ge = new GenericEvent();
              ge.setEventSubType( "RefreshFolderEvent" );
              ge.setStringParam( parentPath );

              EventBusUtil.EVENT_BUS.fireEvent( ge );

            } else {
              MessageDialogBox dialogBox =
                  new MessageDialogBox(
                      Messages.getString( "error" ), Messages.getString( "operationPermissionDenied" ), false, false, true ); //$NON-NLS-1$
              dialogBox.center();
            }
          }
        };
      }
      requestBuilder.setCallback( requestCallback );
    }

    // start the chain
    try {
      if ( !requestBuilders.isEmpty() ) {
        WaitPopup.getInstance().setVisible( true );
        requestBuilders.get( 0 ).send();
      }
    } catch ( RequestException e ) {
      // ignored
    }
  }

  /**
   * @param tab
   */
  public void showTab( Tabs tab ) {
    for ( int i = 0; i < propertyTabs.getTabCount(); i++ ) {
      PentahoTab pTab = propertyTabs.getTab( i );
      switch ( tab ) {
        case GENERAL:
          if ( pTab.getContent() == generalTab ) {
            propertyTabs.selectTab( pTab );
          }
          break;
        case PERMISSION:
          if ( canManageAcls && pTab.getContent() == permissionsTab ) {
            propertyTabs.selectTab( pTab );
          }
          break;
        default:
          break;
      }
    }
  }

  /**
   * @param fileSummary
   */
  protected void getAcls( RepositoryFile fileSummary ) {
    String url = contextURL + "api/repo/files/" + SolutionBrowserPanel.pathToId( fileSummary.getPath() ) + "/acl"; //$NON-NLS-1$ //$NON-NLS-2$
    RequestBuilder builder = new RequestBuilder( RequestBuilder.GET, url );
    // This header is required to force Internet Explorer to not cache values from the GET response.
    builder.setHeader( "If-Modified-Since", "01 Jan 1970 00:00:00 GMT" );
    try {
      builder.sendRequest( null, new RequestCallback() {

        public void onError( Request request, Throwable exception ) {
          MessageDialogBox dialogBox =
              new MessageDialogBox( Messages.getString( "error" ), exception.getLocalizedMessage(), false, false, true ); //$NON-NLS-1$
          dialogBox.center();
        }

        public void onResponseReceived( Request request, Response response ) {
          if ( response.getStatusCode() == Response.SC_OK ) {
            generalTab.setAclResponse( response );
            if ( permissionsTab != null ) {
              permissionsTab.setAclResponse( response );
            }
          } else {
            MessageDialogBox dialogBox =
                new MessageDialogBox(
                    Messages.getString( "error" ), Messages.getString( "serverErrorColon" ) + " " + response.getStatusCode(), false, false, true ); //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
            dialogBox.center();
          }
        }
      } );
    } catch ( RequestException e ) {
      MessageDialogBox dialogBox =
          new MessageDialogBox( Messages.getString( "error" ), e.getLocalizedMessage(), false, false, true ); //$NON-NLS-1$
      dialogBox.center();
    }
  }

  /**
   * @param fileSummary
   */
  protected void getMetadata( RepositoryFile fileSummary ) {
    String metadataUrl =
        contextURL
            + "api/repo/files/" + SolutionBrowserPanel.pathToId( fileSummary.getPath() ) + "/metadata?cb=" + System.currentTimeMillis(); //$NON-NLS-1$ //$NON-NLS-2$
    RequestBuilder metadataBuilder = new RequestBuilder( RequestBuilder.GET, metadataUrl );
    // This header is required to force Internet Explorer to not cache values from the GET response.
    metadataBuilder.setHeader( "If-Modified-Since", "01 Jan 1970 00:00:00 GMT" );
    metadataBuilder.setHeader( "accept", "application/json" );
    try {
      metadataBuilder.sendRequest( null, new RequestCallback() {

        public void onError( Request request, Throwable exception ) {
          MessageDialogBox dialogBox =
              new MessageDialogBox( Messages.getString( "error" ), exception.getLocalizedMessage(), false, false, true ); //$NON-NLS-1$
          dialogBox.center();
        }

        public void onResponseReceived( Request request, Response response ) {
          if ( response.getStatusCode() == Response.SC_OK ) {
            if ( response.getText() != null && !"".equals( response.getText() ) && !response.getText().equals( "null" ) ) {
              generalTab.setMetadataResponse( response );
            }
          } else {
            MessageDialogBox dialogBox =
                new MessageDialogBox(
                    Messages.getString( "error" ), Messages.getString( "serverErrorColon" ) + " " + response.getStatusCode(), false, false, true ); //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
            dialogBox.center();
          }
        }
      } );
    } catch ( RequestException e ) {
      MessageDialogBox dialogBox =
          new MessageDialogBox( Messages.getString( "error" ), e.getLocalizedMessage(), false, false, true ); //$NON-NLS-1$
      dialogBox.center();
    }
  }

  /**
   *
   */
  protected class ChainedRequestCallback implements RequestCallback {
    RequestBuilder nextRequest;

    /**
     * @param arg0
     * @param arg1
     */
    @Override
    public void onError( Request arg0, Throwable arg1 ) {
      WaitPopup.getInstance().setVisible( false );
      MessageDialogBox dialogBox =
          new MessageDialogBox( Messages.getString( "error" ), arg1.toString(), false, false, true ); //$NON-NLS-1$
      dialogBox.center();
    }

    /**
     * @param arg0
     * @param arg1
     */
    @Override
    public void onResponseReceived( Request arg0, Response arg1 ) {
      if ( arg1.getStatusCode() == Response.SC_OK ) {
        try {
          nextRequest.send();
        } catch ( RequestException e ) {
          // ignored
        }
        dirty = false;
      } else {
        WaitPopup.getInstance().setVisible( false );
        MessageDialogBox dialogBox =
            new MessageDialogBox(
                Messages.getString( "error" ), Messages.getString( "operationPermissionDenied" ), false, false, true ); //$NON-NLS-1$
        dialogBox.center();
      }
    }

    /**
     * @param nextRequest
     */
    public void setNextRequest( RequestBuilder nextRequest ) {
      this.nextRequest = nextRequest;
    }

    /**
     * @param nextRequest
     */
    public ChainedRequestCallback( RequestBuilder nextRequest ) {
      this.nextRequest = nextRequest;
    }

    /**
     *
     */
    public ChainedRequestCallback() {

    }
  }
}
