/**
 *  Copyright (C) 2003-2010 eXo Platform SAS.
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Affero General Public License
 *  as published by the Free Software Foundation; either version 3
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see<http://www.gnu.org/licenses/>.
 */

package org.exoplatform.ecms.xcmis.sp.jcr.exo;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ObjectParameter;
import org.exoplatform.ecms.xcmis.sp.jcr.exo.index.CmisContentReader;
import org.exoplatform.ecms.xcmis.sp.jcr.exo.index.CmisSchema;
import org.exoplatform.ecms.xcmis.sp.jcr.exo.index.CmisSchemaTableResolver;
import org.exoplatform.ecms.xcmis.sp.jcr.exo.index.IndexListener;
import org.exoplatform.services.document.DocumentReaderService;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.config.RepositoryConfigurationException;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.core.WorkspaceContainerFacade;
import org.exoplatform.services.jcr.impl.core.RepositoryImpl;
import org.exoplatform.services.jcr.impl.core.observation.ObservationManagerRegistry;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.picocontainer.Startable;
import org.xcmis.search.SearchService;
import org.xcmis.search.SearchServiceException;
import org.xcmis.search.config.IndexConfiguration;
import org.xcmis.search.config.SearchServiceConfiguration;
import org.xcmis.search.content.command.InvocationContext;
import org.xcmis.search.value.SlashSplitter;
import org.xcmis.search.value.ToStringNameConverter;
import org.xcmis.spi.CmisConstants;
import org.xcmis.spi.CmisRegistry;
import org.xcmis.spi.CmisRuntimeException;
import org.xcmis.spi.Connection;
import org.xcmis.spi.InvalidArgumentException;
import org.xcmis.spi.PermissionService;
import org.xcmis.spi.StorageProvider;
import org.xcmis.spi.model.BaseType;

import java.util.HashMap;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventListenerIterator;

/**
 * @author <a href="mailto:andrey00x@gmail.com">Andrey Parfonov</a>
 * @version $Id: StorageProviderImpl.java 1262 2010-06-09 10:07:01Z andrew00x $
 */
public class StorageProviderImpl implements StorageProvider, Startable
{

   public static class StorageProviderConfig
   {
      /** The storage configuration. */
      private StorageConfiguration storage;

      public StorageProviderConfig(StorageConfiguration storage)
      {
         this.storage = storage;
      }

      public StorageProviderConfig()
      {
      }

      /**
       * @return the storage configuration
       */
      public StorageConfiguration getStorage()
      {
         return storage;
      }

      /**
       * @param storage the storage configuration
       */
      public void setStorage(StorageConfiguration storage)
      {
         this.storage = storage;
      }
   }

   /** Logger. */
   private static final Log LOG = ExoLogger.getLogger(StorageProviderImpl.class);

   /** JCR repository service. */
   private final RepositoryService repositoryService;

   /** Document reader service. */
   private final DocumentReaderService documentReaderService;

   /** Permission service. */
   private final PermissionService permissionService;

   private final CmisRegistry registry;

   private SearchService searchService;

   private StorageConfiguration storageConfiguration;

   Map<String, TypeMapping> nodeTypeMapping = new HashMap<String, TypeMapping>();

   /**
    * This constructor is used by eXo container.
    * 
    * @param repositoryService JCR repository service
    * @param documentReaderService DocumentReaderService required for indexing
    *        mechanism
    * @param permissionService PermissionService
    * @param registry CmisRegistry will be used for registered current
    *        StorageProvider after its initialization
    * @param initParams configuration parameters
    */
   public StorageProviderImpl(RepositoryService repositoryService, DocumentReaderService documentReaderService,
      PermissionService permissionService, CmisRegistry registry, InitParams initParams)
   {
      this(repositoryService, documentReaderService, permissionService, registry, null,
         getStorageConfiguration(initParams));
   }

   /**
    * This constructor is used by eXo container.
    * 
    * @param repositoryService JCR repository service
    * @param permissionService PermissionService
    * @param registry CmisRegistry will be used for registered current
    *        StorageProvider after its initialization
    * @param initParams configuration parameters
    */
   public StorageProviderImpl(RepositoryService repositoryService, PermissionService permissionService,
      CmisRegistry registry, InitParams initParams)
   {
      this(repositoryService, null, permissionService, registry, null, getStorageConfiguration(initParams));
   }

   private static StorageConfiguration getStorageConfiguration(InitParams initParams)
   {
      StorageConfiguration storageConfiguration = null;
      if (initParams != null)
      {
         ObjectParameter param = initParams.getObjectParam("configuration");
         if (param != null)
         {
            StorageProviderConfig confs = (StorageProviderConfig)param.getObject();
            storageConfiguration = confs.getStorage();
         }
      }
      return storageConfiguration;
   }

   StorageProviderImpl(RepositoryService repositoryService, DocumentReaderService documentReaderService,
      PermissionService permissionService, CmisRegistry registry, StorageConfiguration storageConfiguration)
   {
      this(repositoryService, documentReaderService, permissionService, registry, null, storageConfiguration);
   }

   StorageProviderImpl(RepositoryService repositoryService, DocumentReaderService documentReaderService,
      PermissionService permissionService, SearchService searchService, StorageConfiguration storageConfiguration)
   {
      this(repositoryService, documentReaderService, permissionService, null, searchService, storageConfiguration);
   }

   StorageProviderImpl(RepositoryService repositoryService, DocumentReaderService documentReaderService,
      PermissionService permissionService, CmisRegistry registry, SearchService searchService,
      StorageConfiguration storageConfiguration)
   {
      this.repositoryService = repositoryService;
      this.documentReaderService = documentReaderService;
      this.permissionService = permissionService;
      this.registry = registry;
      this.searchService = searchService;
      this.storageConfiguration = storageConfiguration;
      // Unstructured mapping immediately. May need have access
      // to root node which often has type nt:unstructured.
      nodeTypeMapping.put(JcrCMIS.NT_UNSTRUCTURED, new TypeMapping(JcrCMIS.NT_UNSTRUCTURED, BaseType.FOLDER,
         CmisConstants.FOLDER));
      nodeTypeMapping.put("exo:taxonomy", new TypeMapping("exo:taxonomy", BaseType.FOLDER, CmisConstants.FOLDER));
   }

   /**
    * {@inheritDoc}
    */
   public Connection getConnection()
   {
      if (storageConfiguration == null)
      {
         throw new InvalidArgumentException("CMIS repository is not configured.");
      }

      String repositoryId = storageConfiguration.getRepository();
      String ws = storageConfiguration.getWorkspace();

      try
      {
         ManageableRepository repository = repositoryService.getRepository(repositoryId);
         Session session = repository.login(ws);
         StorageImpl storage = null;
         if (searchService != null)
         {
            storage =
               new QueryableStorage(session, storageConfiguration, searchService, permissionService, nodeTypeMapping);
            IndexListener indexListener = new IndexListener(storage, searchService);
            storage.setIndexListener(indexListener);
         }
         else
         {
            storage = new StorageImpl(session, storageConfiguration, permissionService, nodeTypeMapping);
         }
         return new JcrConnection(storage);
      }
      catch (RepositoryException re)
      {
         throw new CmisRuntimeException("Unable get CMIS storage " + storageConfiguration.getId() + ". "
            + re.getMessage(), re);
      }
      catch (RepositoryConfigurationException rce)
      {
         throw new CmisRuntimeException("Unable get CMIS storage " + storageConfiguration.getId() + ". "
            + rce.getMessage(), rce);
      }
   }

   /**
    * {@inheritDoc}
    */
   public String getStorageID()
   {
      if (storageConfiguration == null)
      {
         throw new InvalidArgumentException("CMIS storage is not configured.");
      }
      return storageConfiguration.getId();
   }

   /**
    * Set storage configuration.
    * 
    * @param storageConfig storage configuration
    * @throw IllegalStateException if configuration for storage already set
    */
   void setConfiguration(StorageConfiguration storageConfig)
   {
      if (this.storageConfiguration != null)
      {
         throw new IllegalStateException("Storage configuration already set.");
      }
      this.storageConfiguration = storageConfig;
   }

   /**
    * {@inheritDoc}
    */
   public void start()
   {
      try
      {
         init();
         registry.addStorage(this);
      }
      catch (Throwable e)
      {
         LOG.error("Unable to initialize storage. ", e);
      }
   }

   protected synchronized void init() throws RepositoryException, RepositoryConfigurationException,
      SearchServiceException
   {
      if (storageConfiguration == null)
      {
         throw new CmisRuntimeException("CMIS repository is not configured.");
      }

      ManageableRepository repository = repositoryService.getRepository(storageConfiguration.getRepository());
      Session session = repository.getSystemSession(storageConfiguration.getWorkspace());
      Node root = session.getRootNode();

      Node xCmisSystem = session.itemExists(StorageImpl.XCMIS_SYSTEM_PATH) //
         ? (Node)session.getItem(StorageImpl.XCMIS_SYSTEM_PATH) //
         : root.addNode(StorageImpl.XCMIS_SYSTEM_PATH.substring(1), "xcmis:system");

      if (!xCmisSystem.hasNode(StorageImpl.XCMIS_WORKING_COPIES))
      {
         xCmisSystem.addNode(StorageImpl.XCMIS_WORKING_COPIES, "xcmis:workingCopies");
         if (LOG.isDebugEnabled())
         {
            LOG.debug("CMIS Working Copies store " + StorageImpl.XCMIS_SYSTEM_PATH + "/"
               + StorageImpl.XCMIS_WORKING_COPIES + " created.");
         }
      }

      if (!xCmisSystem.hasNode(StorageImpl.XCMIS_RELATIONSHIPS))
      {
         xCmisSystem.addNode(StorageImpl.XCMIS_RELATIONSHIPS, "xcmis:relationships");
         if (LOG.isDebugEnabled())
         {
            LOG.debug("CMIS relationship store " + StorageImpl.XCMIS_SYSTEM_PATH + "/"
               + StorageImpl.XCMIS_RELATIONSHIPS + " created.");
         }
      }

      if (!xCmisSystem.hasNode(StorageImpl.XCMIS_POLICIES))
      {
         xCmisSystem.addNode(StorageImpl.XCMIS_POLICIES, "xcmis:policies");
         if (LOG.isDebugEnabled())
         {
            LOG.debug("CMIS policies store " + StorageImpl.XCMIS_SYSTEM_PATH + "/" + StorageImpl.XCMIS_POLICIES
               + " created.");
         }
      }

      session.save();

      Boolean persistRenditions = (Boolean)storageConfiguration.getProperties().get("exo.cmis.renditions.persistent");
      if (persistRenditions == null)
      {
         persistRenditions = false;
      }
      if (persistRenditions)
      {
         Workspace workspace = session.getWorkspace();
         try
         {
            boolean exist = false;

            // TODO can do this simpler ?
            WorkspaceContainerFacade workspaceContainer =
               ((RepositoryImpl)repository).getWorkspaceContainer(workspace.getName());
            ObservationManagerRegistry observationManagerRegistry =
               (ObservationManagerRegistry)workspaceContainer.getComponent(ObservationManagerRegistry.class);

            for (EventListenerIterator iter = observationManagerRegistry.getEventListeners(); iter.hasNext();)
            {
               if (iter.nextEventListener().getClass() == RenditionsUpdateListener.class)
               {
                  exist = true;
                  break;
               }
            }
            if (!exist)
            {
               workspace.getObservationManager().addEventListener(
                  new RenditionsUpdateListener(repository, storageConfiguration.getWorkspace()),
                  Event.PROPERTY_ADDED | Event.PROPERTY_CHANGED, "/", true, null, new String[]{JcrCMIS.NT_RESOURCE},
                  false);
            }
         }
         catch (RepositoryException e)
         {
            LOG.error("Unable to create event listener. " + e);
         }
      }

      if (searchService == null && storageConfiguration.getIndexConfiguration() != null)
      {
         //prepare search service
         StorageImpl storage = new StorageImpl(session, storageConfiguration, permissionService, nodeTypeMapping);
         CmisSchema schema = new CmisSchema(storage);
         CmisSchemaTableResolver tableResolver =
            new CmisSchemaTableResolver(new ToStringNameConverter(), schema, storage);

         IndexConfiguration indexConfiguration = storageConfiguration.getIndexConfiguration();
         indexConfiguration.setRootUuid(storage.getRepositoryInfo().getRootFolderId());
         //if list of root parents is empty it will be indexed as empty string
         indexConfiguration.setRootParentUuid("");
         indexConfiguration.setDocumentReaderService(documentReaderService);

         //default invocation context
         InvocationContext invocationContext = new InvocationContext();
         invocationContext.setNameConverter(new ToStringNameConverter());
         invocationContext.setSchema(schema);
         invocationContext.setPathSplitter(new SlashSplitter());
         invocationContext.setTableResolver(tableResolver);

         SearchServiceConfiguration configuration = new SearchServiceConfiguration();
         configuration.setIndexConfiguration(indexConfiguration);
         configuration.setContentReader(new CmisContentReader(storage));
         configuration.setNameConverter(new ToStringNameConverter());
         configuration.setDefaultInvocationContext(invocationContext);
         configuration.setTableResolver(tableResolver);
         configuration.setPathSplitter(new SlashSplitter());

         SearchService searchService = new SearchService(configuration);
         searchService.start();

         //attach listener to the created storage
         IndexListener indexListener = new IndexListener(storage, searchService);
         storage.setIndexListener(indexListener);

         this.searchService = searchService;
      }
   }

   /**
    * {@inheritDoc}
    */
   public void stop()
   {
      if (searchService != null)
      {
         searchService.stop();
      }
   }

   public StorageConfiguration getStorageConfiguration()
   {
      return storageConfiguration;
   }

   void addNodeTypeMapping(Map<String, TypeMapping> nodeTypeMapping)
   {
      this.nodeTypeMapping.putAll(nodeTypeMapping);
   }
}
