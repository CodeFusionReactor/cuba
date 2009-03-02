/*
 * Copyright (c) 2008 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.

 * Author: Konstantin Krivopustov
 * Created: 01.11.2008 13:24:28
 * $Id$
 */
package com.haulmont.cuba.core.sys;

import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.EntityManagerFactory;
import com.haulmont.cuba.core.PersistenceProvider;
import com.haulmont.cuba.core.sys.persistence.EntityLifecycleListener;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.openjpa.persistence.OpenJPAEntityManagerFactory;
import org.apache.openjpa.persistence.OpenJPAEntityManagerFactorySPI;
import org.apache.openjpa.persistence.OpenJPAPersistence;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.transaction.*;
import java.util.Hashtable;
import java.util.Map;

public class ManagedPersistenceProvider extends PersistenceProvider
{
    private Context jndiContext;

    private final Object mutex = new Object();

    private boolean emfInitialized;

    private Map<javax.transaction.Transaction, EntityManager> emMap = 
            new Hashtable<javax.transaction.Transaction, EntityManager>();

    private ThreadLocal<EntityManager> emThreadLocal = new ThreadLocal<EntityManager>();

    public static final String EMF_JNDI_NAME = "EntityManagerFactoryAdapterImpl";

    public static final String TM_JNDI_NAME = "java:/TransactionManager";

    private Log log = LogFactory.getLog(ManagedPersistenceProvider.class);

    public ManagedPersistenceProvider(Context jndiContext) {
        this.jndiContext = jndiContext;
    }

    protected EntityManagerFactory __getEntityManagerFactory() {
        synchronized (mutex) {
            if (!emfInitialized) {
                log.debug("Creating new EntityManagerFactory");

                String xmlPath = getPersistenceXmlPath();
                String unitName = getPersistenceUnitName();
                log.debug(String.format("Using persistence unit %s from %s", unitName, xmlPath));

                OpenJPAEntityManagerFactory jpaFactory =
                        OpenJPAPersistence.createEntityManagerFactory(unitName, xmlPath);
                initJpaFactory(jpaFactory);

                EntityManagerFactory emf = new EntityManagerFactoryImpl(jpaFactory);
                try {
                    log.debug("Binding new EntityManagerFactory to JNDI context " + EMF_JNDI_NAME);
                    jndiContext.bind(EMF_JNDI_NAME, emf);
                } catch (NamingException e) {
                    throw new RuntimeException(e);
                }
                emfInitialized = true;
            }
        }
        try {
            return (EntityManagerFactory) jndiContext.lookup(EMF_JNDI_NAME);
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

    private void initJpaFactory(OpenJPAEntityManagerFactory jpaFactory) {
        ((OpenJPAEntityManagerFactorySPI) jpaFactory).addLifecycleListener(
                new EntityLifecycleListener(),
                null
        );
    }

    protected EntityManager __getEntityManager() {
        EntityManager em;
        try {
            TransactionManager tm = getTransactionManager();
            Transaction tx = tm.getTransaction();
            if (tx != null) {
                em = emMap.get(tx);
                if (em == null) {
                    log.trace("Creating new EntityManager for transaction " + tx);
                    if (tx.getStatus() != Status.STATUS_ACTIVE)
                        throw new RuntimeException("Unable to create an EntityManager: JTA transaction status=" + tx.getStatus());

                    // First register synchronization and then create EntityManager -
                    // to ensure correct order of BrokerImpl.afterCompletion() and BrokerImpl.close() invocations.
                    TxSync sync = new TxSync(tx);
                    try {
                        tx.registerSynchronization(sync);
                    } catch (RollbackException e) {
                        throw new RuntimeException("Unable to register synchronization with JTA transaction", e);
                    }

                    em = getEntityManagerFactory().createEntityManager();

                    sync.setEntityManager(em);
                    emMap.put(tx, em);
                }
            }
            else {
                log.trace("Creating new non-transactional EntityManager");
                em = emThreadLocal.get();
                if (em == null || em.isClosed()) {
                    em = __getEntityManagerFactory().createEntityManager();
                    ((EntityManagerImpl) em).addCloseListener(
                            new EntityManagerImpl.CloseListener()
                            {
                                public void onClose() {
                                    emThreadLocal.remove();
                                }
                            }
                    );
                    emThreadLocal.set(em);
                }
            }
            return em;
        } catch (NamingException e) {
            throw new RuntimeException(e);
        } catch (SystemException e) {
            throw new RuntimeException(e);
        }
    }

    private TransactionManager getTransactionManager() throws NamingException {
        return (TransactionManager) jndiContext.lookup(TM_JNDI_NAME);
    }

    private class TxSync implements Synchronization
    {
        private Transaction tx;
        private EntityManager em;

        public TxSync(Transaction tx) {
            this.tx = tx;
        }

        public void setEntityManager(EntityManager em) {
            this.em = em;
        }

        public void beforeCompletion() {
            // Flush and detach in-place all managed instances.
            // By default OpenJPA detaches in afterCompletion tx synchronization method,
            // which leads to error on attempt to synchronize versions for some objects. 
            log.trace("Detaching entities: tx=" + tx);
            ((EntityManagerImpl) em).detachAll();
        }

        public void afterCompletion(int i) {
            log.trace("Closing EntityManager: tx=" + tx);
            em.close();
            emMap.remove(tx);
        }
    }
}
