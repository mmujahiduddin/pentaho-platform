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


package org.pentaho.test.platform.engine.core;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.pentaho.platform.api.engine.ILogger;
import org.pentaho.platform.api.engine.IPentahoDefinableObjectFactory;
import org.pentaho.platform.api.engine.IPentahoDefinableObjectFactory.Scope;
import org.pentaho.platform.api.engine.IPentahoObjectFactory;
import org.pentaho.platform.api.engine.ISolutionEngine;
import org.pentaho.platform.engine.core.system.PathBasedSystemSettings;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.platform.engine.core.system.boot.PentahoSystemBoot;
import org.pentaho.platform.engine.core.system.boot.PlatformInitializationException;
import org.pentaho.platform.engine.core.system.objfac.StandaloneSpringPentahoObjectFactory;

/**
 * This is a test-oriented booter class that extends {@link PentahoSystemBoot}.
 * 
 * @author aphillips
 * @see PentahoSystemBoot
 */
@SuppressWarnings( "nls" )
public class MicroPlatform extends PentahoSystemBoot {

  private static Log logger = LogFactory.getLog( MicroPlatform.class );

  protected RepositoryModule repositoryModule;

  public MicroPlatform() {
    super();
  }

  public MicroPlatform( String solutionPath ) {
    super( solutionPath );
  }

  public MicroPlatform( String solutionPath, String fullyQualifiedServerUrl ) {
    super( solutionPath, fullyQualifiedServerUrl );
  }

  public MicroPlatform( String solutionPath, IPentahoDefinableObjectFactory factory ) {
    super( solutionPath, factory );
  }

  public MicroPlatform( String solutionPath, String fullyQualifiedServerUrl, IPentahoDefinableObjectFactory factory ) {
    super( solutionPath, fullyQualifiedServerUrl, factory );
  }

  public MicroPlatform defineInstance( Class<?> interfaceClass, Object instance ) {
    defineInstance( interfaceClass.getSimpleName(), instance );
    return this;
  }

  public MicroPlatform defineInstance( String key, Object instance ) {
    if ( getFactory() instanceof IPentahoDefinableObjectFactory ) {
      IPentahoDefinableObjectFactory definableFactory = (IPentahoDefinableObjectFactory) getFactory();
      definableFactory.defineInstance( key, instance );
    } else {
      throw new NoSuchMethodError( "define is only supported by IPentahoDefinableObjectFactory" );
    }
    return this;
  }

  public MicroPlatform.RepositoryModule getRepositoryModule() {
    if ( repositoryModule != null ) {
      return repositoryModule;
    }
    define( "RepositoryModule", "org.pentaho.platform.repository2.unified.JcrRepositoryModule", Scope.GLOBAL );
    RepositoryModule module = PentahoSystem.get( RepositoryModule.class );
    if ( module == null ) {
      throw new IllegalStateException(
          "In order to use the RepositoryModule, you must include pentaho-bi-platform-repository.jar in your classpath" );
    }
    module.setOwner( this );
    this.repositoryModule = module;
    return module;
  }

  @Override
  public void setFullyQualifiedServerUrl( String fullyQualifiedServerUrl ) {
    logger.info( "Fully Qualified Server URL set to " + fullyQualifiedServerUrl );
    super.setFullyQualifiedServerUrl( fullyQualifiedServerUrl );
    // have to set the application context now, so the FQSURL will be available to clients
    // without having to run #start()
    PentahoSystem.setApplicationContext( createApplicationContext() );
  }

  @Override
  public boolean start() throws PlatformInitializationException {
    PentahoSystem.setSystemSettingsService( new PathBasedSystemSettings() );
    boolean ret = super.start();
    // set log levels
    // FIXME: find a better way to set log levels programmatically than this.. this can cause NPEs
    // and other errors, not to mention it's inefficient
    try {
      Object o = PentahoSystem.get( ISolutionEngine.class );
      if ( o != null && o instanceof ILogger ) {
        ( (ILogger) o ).setLoggingLevel( ILogger.DEBUG );
      }
    } catch ( Throwable e ) {
      logger.error( "Failed to set DEBUG log level due to ISolutionEngine not being available in MicroPlatform" );
    }
    return ret;
  }

  public interface Module {
    void setOwner( MicroPlatform mp );

    public void up() throws Exception;

    public void down();
  }

  public interface RepositoryModule extends Module {
    public void login( final String username, final String tenantId );

    public void logout();
  }

  /**
   * Sets the StandaloneSpringPentahoObjectFactory for the Pentaho platform by xml path
   * 
   * @return
   */
  public void setSpringConfig( String cfgFile ) {
    IPentahoObjectFactory pentahoObjectFactory = new StandaloneSpringPentahoObjectFactory();
    pentahoObjectFactory.init( cfgFile, null );
    setFactory( pentahoObjectFactory );
  }
}
