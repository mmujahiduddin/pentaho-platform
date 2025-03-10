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


package org.pentaho.platform.uifoundation.chart;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.StandardEntityCollection;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.imagemap.ImageMapUtilities;
import org.jfree.data.general.Dataset;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeSeriesCollection;
import org.pentaho.commons.connection.IPentahoResultSet;
import org.pentaho.platform.api.engine.IPentahoRequestContext;
import org.pentaho.platform.api.engine.IPentahoUrlFactory;
import org.pentaho.platform.api.repository2.unified.RepositoryFilePermission;
import org.pentaho.platform.engine.core.system.PentahoRequestContextHolder;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.platform.engine.services.runtime.TemplateUtil;
import org.pentaho.platform.uifoundation.messages.Messages;
import org.pentaho.platform.util.messages.LocaleHelper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TimeSeriesCollectionChartComponent extends AbstractJFreeChartComponent {
  private static final long serialVersionUID = -6268840271596447555L;

  protected String seriesName = null;

  public TimeSeriesCollectionChartComponent( final int chartType, final String definitionPath, final int width,
      final int height, final IPentahoUrlFactory urlFactory, final List messages ) {
    super( chartType, definitionPath, width, height, urlFactory, messages );
  }

  public TimeSeriesCollectionChartComponent( final String definitionPath, final IPentahoUrlFactory urlFactory,
      final ArrayList messages ) {
    super( definitionPath, urlFactory, messages );
  }

  public TimeSeriesCollectionChartComponent( final IPentahoUrlFactory urlFactory, final List messages ) {
    super( urlFactory, messages );
  }

  public void setSeriesName( final String seriesName ) {
    this.seriesName = seriesName;
  }

  @Override
  public Dataset createChart( final Document doc ) {
    if ( actionPath != null ) { // if we have a solution then get the values
      values = getActionData();
    }

    if ( values == null ) {
      // we could not get any data
      return null;
    }
    // get the chart node from the document
    Node chartAttributes = doc.selectSingleNode( "//" + AbstractChartComponent.CHART_NODE_NAME ); //$NON-NLS-1$
    // create the definition
    TimeSeriesCollectionChartDefinition chartDefinition =
        new TimeSeriesCollectionChartDefinition( (IPentahoResultSet) values, byRow, chartAttributes, getSession() );

    // set the misc values from chartDefinition
    setChartType( chartDefinition.getChartType() );
    setTitle( chartDefinition.getTitle() );

    // get the URL template
    Node urlTemplateNode = chartAttributes.selectSingleNode( AbstractChartComponent.URLTEMPLATE_NODE_NAME );
    if ( urlTemplateNode != null ) {
      setUrlTemplate( urlTemplateNode.getText() );
    }

    // get the additional parameter
    Node paramName2Node = chartAttributes.selectSingleNode( AbstractChartComponent.PARAM2_NODE_NAME );
    if ( paramName2Node != null ) {
      seriesName = paramName2Node.getText();
    }

    if ( ( chartDefinition.getWidth() != -1 ) && ( width == -1 ) ) {
      setWidth( chartDefinition.getWidth() );
    }
    if ( ( chartDefinition.getHeight() != -1 ) && ( height == -1 ) ) {
      setHeight( chartDefinition.getHeight() );
    }

    return chartDefinition;
  }

  @Override
  public Document getXmlContent() {

    // Create a document that describes the result
    Document result = DocumentHelper.createDocument();
    IPentahoRequestContext requestContext = PentahoRequestContextHolder.getRequestContext();
    setXslProperty( "baseUrl", requestContext.getContextPath() ); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    setXslProperty( "fullyQualifiedServerUrl", PentahoSystem.getApplicationContext().getFullyQualifiedServerURL() ); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    String mapName = "chart" + AbstractChartComponent.chartCount++; //$NON-NLS-1$
    Document chartDefinition = jcrHelper.getSolutionDocument( definitionPath, RepositoryFilePermission.READ );

    if ( chartDefinition == null ) {
      Element errorElement = result.addElement( "error" ); //$NON-NLS-1$
      errorElement
          .addElement( "title" ).setText( Messages.getInstance().getString( "ABSTRACTCHARTEXPRESSION.ERROR_0001_ERROR_GENERATING_CHART" ) ); //$NON-NLS-1$ //$NON-NLS-2$
      String message = Messages.getInstance().getString( "CHARTS.ERROR_0001_CHART_DEFINIION_MISSING", definitionPath ); //$NON-NLS-1$
      errorElement.addElement( "message" ).setText( message ); //$NON-NLS-1$
      error( message );
      return result;
    }
    // create a pie definition from the XML definition
    dataDefinition = createChart( chartDefinition );

    if ( dataDefinition == null ) {
      Element errorElement = result.addElement( "error" ); //$NON-NLS-1$
      errorElement
          .addElement( "title" ).setText( Messages.getInstance().getString( "ABSTRACTCHARTEXPRESSION.ERROR_0001_ERROR_GENERATING_CHART" ) ); //$NON-NLS-1$ //$NON-NLS-2$
      String message = Messages.getInstance().getString( "CHARTS.ERROR_0002_CHART_DATA_MISSING", actionPath ); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
      errorElement.addElement( "message" ).setText( message ); //$NON-NLS-1$
      // System .out.println( result.asXML() );
      return result;
    }

    // create an image for the dial using the JFreeChart engine
    PrintWriter printWriter = new PrintWriter( new StringWriter() );
    // we'll dispay the title in HTML so that the dial image does not have
    // to
    // accommodate it
    String chartTitle = ""; //$NON-NLS-1$
    try {
      if ( width == -1 ) {
        width = Integer.parseInt( chartDefinition.selectSingleNode( "/chart/width" ).getText() ); //$NON-NLS-1$
      }
      if ( height == -1 ) {
        height = Integer.parseInt( chartDefinition.selectSingleNode( "/chart/height" ).getText() ); //$NON-NLS-1$
      }
    } catch ( Exception e ) {
      // go with the default
    }
    if ( chartDefinition.selectSingleNode( "/chart/" + AbstractChartComponent.URLTEMPLATE_NODE_NAME ) != null ) { //$NON-NLS-1$
      urlTemplate =
          chartDefinition.selectSingleNode( "/chart/" + AbstractChartComponent.URLTEMPLATE_NODE_NAME ).getText(); //$NON-NLS-1$
    }

    if ( chartDefinition.selectSingleNode( "/chart/paramName" ) != null ) { //$NON-NLS-1$
      paramName = chartDefinition.selectSingleNode( "/chart/paramName" ).getText(); //$NON-NLS-1$
    }

    Element root = result.addElement( "charts" ); //$NON-NLS-1$
    TimeSeriesCollection chartDataDefinition = (TimeSeriesCollection) dataDefinition;
    if ( chartDataDefinition.getSeriesCount() > 0 ) {
      // create temporary file names
      String[] tempFileInfo = createTempFile();
      String fileName = tempFileInfo[AbstractChartComponent.FILENAME_INDEX];
      String filePathWithoutExtension = tempFileInfo[AbstractChartComponent.FILENAME_WITHOUT_EXTENSION_INDEX];

      ChartRenderingInfo info = new ChartRenderingInfo( new StandardEntityCollection() );
      JFreeChartEngine.saveChart( chartDataDefinition, chartTitle,
          "", filePathWithoutExtension, width, height, JFreeChartEngine.OUTPUT_PNG, printWriter, info, this ); //$NON-NLS-1$
      applyOuterURLTemplateParam();
      populateInfo( info );
      Element chartElement = root.addElement( "chart" ); //$NON-NLS-1$
      chartElement.addElement( "mapName" ).setText( mapName ); //$NON-NLS-1$
      chartElement.addElement( "width" ).setText( Integer.toString( width ) ); //$NON-NLS-1$
      chartElement.addElement( "height" ).setText( Integer.toString( height ) ); //$NON-NLS-1$
      for ( int row = 0; row < chartDataDefinition.getSeriesCount(); row++ ) {
        for ( int column = 0; column < chartDataDefinition.getItemCount( row ); column++ ) {
          Number value = chartDataDefinition.getY( row, column );
          Comparable rowKey = chartDataDefinition.getSeriesKey( row );
          RegularTimePeriod columnKey = chartDataDefinition.getSeries( row ).getTimePeriod( column );
          Element valueElement = chartElement.addElement( "value2D" ); //$NON-NLS-1$
          valueElement.addElement( "value" ).setText( value.toString() ); //$NON-NLS-1$
          valueElement.addElement( "row-key" ).setText( rowKey.toString() ); //$NON-NLS-1$
          valueElement.addElement( "column-key" ).setText( columnKey.toString() ); //$NON-NLS-1$
        }
      }
      String mapString = ImageMapUtilities.getImageMap( mapName, info );
      chartElement.addElement( "imageMap" ).setText( mapString ); //$NON-NLS-1$
      chartElement.addElement( "image" ).setText( fileName ); //$NON-NLS-1$
    }
    return result;
  }

  private void populateInfo( final ChartRenderingInfo info ) {
    if ( urlTemplate == null ) {
      return;
    }
    Iterator iter = info.getEntityCollection().iterator();
    while ( iter.hasNext() ) {
      ChartEntity entity = (ChartEntity) iter.next();
      if ( entity instanceof XYItemEntity ) {
        if ( urlTemplate != null ) {
          XYItemEntity xyItemEntity = (XYItemEntity) entity;
          if ( paramName == null ) {
            xyItemEntity.setURLText( urlTemplate );
          } else {
            try {
              int seriesIndex = xyItemEntity.getSeriesIndex();
              int itemIndex = xyItemEntity.getItem();
              String xySeriesKey =
                  (String) ( (TimeSeriesCollection) xyItemEntity.getDataset() ).getSeriesKey( seriesIndex );
              String encodedVal = URLEncoder.encode( xySeriesKey, LocaleHelper.getSystemEncoding() );
              String drillURL = TemplateUtil.applyTemplate( urlTemplate, paramName, encodedVal );
              String itemValueStr =
                  ( (TimeSeriesCollection) xyItemEntity.getDataset() ).getX( seriesIndex, itemIndex ).toString();
              encodedVal = URLEncoder.encode( itemValueStr, LocaleHelper.getSystemEncoding() );
              if ( seriesName == null ) {
                drillURL = TemplateUtil.applyTemplate( drillURL, "SERIES", encodedVal ); //$NON-NLS-1$
              } else {
                drillURL = TemplateUtil.applyTemplate( drillURL, seriesName, encodedVal );
              }
              xyItemEntity.setURLText( drillURL );
            } catch ( UnsupportedEncodingException e ) {
              // TODO Auto-generated catch block
              e.printStackTrace();
            }
          }
        }
      }
    }
  }

  @Override
  public boolean validate() {
    // TODO Auto-generated method stub
    return true;
  }

}
