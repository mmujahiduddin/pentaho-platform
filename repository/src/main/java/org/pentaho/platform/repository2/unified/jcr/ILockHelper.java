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


package org.pentaho.platform.repository2.unified.jcr;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.lock.Lock;
import java.io.Serializable;
import java.util.Date;

/**
 * Helper class that stores, retrieves, and removes lock tokens. In section 8.4.7 of the JSR-170 specification, it
 * states, "the user must additionally ensure that a reference to the lock token is preserved separately so that it
 * can later be attached to another session." This manual step is necessary when using open-scoped locks and this
 * implementation uses open-scoped locks exclusively.
 * 
 * @author mlowery
 */
public interface ILockHelper {

  /**
   * Removes a lock token so that it can never be associated with anyone's session again. (To be called after the
   * file has been unlocked (or the file is deleted) and therefore the token associated with the lock is
   * unnecessary.)
   * 
   * @param session
   *          session
   * @param pentahoJcrConstants
   *          constants
   * @param lock
   *          lock
   * @throws RepositoryException
   *           if anything goes wrong
   */
  void removeLockToken( final Session session, final PentahoJcrConstants pentahoJcrConstants, final Lock lock )
    throws RepositoryException;

  /**
   * Returns {@code true} if user represented by session can potentially unlock the given lock. This can be a
   * function of access control or some other mechanism.
   * 
   * @param session
   *          session
   * @param pentahoJcrConstants
   *          constants
   * @param lock
   *          lock
   * @throws RepositoryException
   *           if anything goes wrong
   */
  boolean canUnlock( final Session session, final PentahoJcrConstants pentahoJcrConstants, final Lock lock )
    throws RepositoryException;

  /**
   * Locks a file.
   * 
   * @param session
   *          session
   * @param pentahoJcrConstants
   *          constants
   * @param fileId
   *          file id
   * @param lockMessage
   *          lock message
   * @throws RepositoryException
   *           if anything goes wrong
   */
  void lockFile( final Session session, final PentahoJcrConstants pentahoJcrConstants, final Serializable fileId,
      final String lockMessage ) throws RepositoryException;

  /**
   * Unlocks a file.
   * 
   * @param session
   *          session
   * @param pentahoJcrConstants
   *          constants
   * @param fileId
   *          file id
   * @throws RepositoryException
   *           if anything goes wrong
   */
  void unlockFile( final Session session, final PentahoJcrConstants pentahoJcrConstants, final Serializable fileId )
    throws RepositoryException;

  /**
   * Adds the lock token associated with the file with the given file id if the file is locked.
   * 
   * @param session
   *          session
   * @param pentahoJcrConstants
   *          constants
   * @param fileId
   *          file id
   * @throws RepositoryException
   *           if anything goes wrong
   */
  void addLockTokenToSessionIfNecessary( final Session session, final PentahoJcrConstants pentahoJcrConstants,
      final Serializable fileId ) throws RepositoryException;

  /**
   * Removes the lock token associated with the file with the given file id
   * 
   * @param session
   *          session
   * @param pentahoJcrConstants
   *          constants
   * @param fileId
   *          file id
   * @throws RepositoryException
   *           if anything goes wrong
   */
  void removeLockTokenFromSessionIfNecessary( final Session session, final PentahoJcrConstants pentahoJcrConstants,
      final Serializable fileId ) throws RepositoryException;

  Date getLockDate( final Session session, final PentahoJcrConstants pentahoJcrConstants, final Lock lock )
    throws RepositoryException;

  String getLockMessage( final Session session, final PentahoJcrConstants pentahoJcrConstants, final Lock lock )
    throws RepositoryException;

  String getLockOwner( final Session session, final PentahoJcrConstants pentahoJcrConstants, final Lock lock )
    throws RepositoryException;
}
