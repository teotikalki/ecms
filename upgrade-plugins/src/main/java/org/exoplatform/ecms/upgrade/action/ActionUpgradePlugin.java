/*
 * Copyright (C) 2016 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.ecms.upgrade.action;

import java.util.ArrayList;
import java.util.List;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.commons.version.util.VersionComparator;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.impl.core.ItemImpl;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * Created by The eXo Platform SAS
 * Author : aboughzela@exoplatform.com
 */
public class ActionUpgradePlugin extends UpgradeProductPlugin{
   private static final Log log = ExoLogger.getLogger(ActionUpgradePlugin.class.getName());

   private RepositoryService repositoryService_;

   /**
    * Define node-type ACTION REFERENCE NODE TYPE
    */
   private static final String ACTIONS_REFERENCE = "exo:actionsReference";

   /**
    * Define node-type ACTION REFERENCE STORAGE
    */
   private static final String REFERENCES_NODE = "jcr:actionsReference";

   private static final String REFERENCE_PROPERTY = "exo:actions";

   private final String WORKSPACE_PROPERTY = "exo:workspace";

   private static final String SYSTEM_NODE = "jcr:system";

   /**
    * Define workspace name used to store reference actions
    */
   private final String TARGET_WORKSPACE = "target-workspace";

   private static final String MIX_REFERENCEABLE = "mix:referenceable" ;

   private String targetWorkspace;

   /**
    * Define query statement
    */
   private static final String ACTION_QUERY = "//element(*, exo:action)";

   public ActionUpgradePlugin(RepositoryService repoService, InitParams initParams)   {
      super(initParams);
      this.repositoryService_ = repoService;

      ValueParam targetWorkspaceParam = initParams.getValueParam(TARGET_WORKSPACE);
      if (targetWorkspaceParam != null)      {
         this.targetWorkspace = targetWorkspaceParam.getValue();
      }

   }

   public void processUpgrade(String oldVersion, String newVersion)   {

      log.info("Start " + this.getClass().getName() + ".............");
      try      {
         ManageableRepository jcrRepository = repositoryService_.getCurrentRepository();
         if (targetWorkspace == null || targetWorkspace.isEmpty())         {
            targetWorkspace = jcrRepository.getConfiguration().getSystemWorkspaceName();
         }
         //system node jcr:system"
         Node systemFolder;
         //Reference node "jcr:actionsReference"
         Node actionsNodeReference = null;
         Session targetSession = jcrRepository.getSystemSession(targetWorkspace);
         systemFolder = targetSession.getRootNode().getNode(SYSTEM_NODE);

         //Get reference node
         if (systemFolder.hasNode(REFERENCES_NODE))         {
            actionsNodeReference = systemFolder.getNode(REFERENCES_NODE);
         }
         else         {
            actionsNodeReference = systemFolder.addNode(REFERENCES_NODE);
            targetSession.save();
         }

         QueryManager queryManager = null;
         Node result;
         //iterate all workspaces
         for (String workspace : jcrRepository.getWorkspaceNames())         {
            Session session = jcrRepository.getSystemSession(workspace);

            queryManager = session.getWorkspace().getQueryManager();
            Query query = queryManager.createQuery(ACTION_QUERY, Query.XPATH);
            QueryResult queryResult = query.execute();
            result = null;
            List<String> array = new ArrayList<String>();

            //iterate stored exo:action nodes
            for (NodeIterator iter = queryResult.getNodes(); iter.hasNext(); )            {
               if(result == null )               {
                  if (actionsNodeReference.hasNode(workspace))                  {
                     result = actionsNodeReference.getNode(workspace);
                     if (result.hasProperty(REFERENCE_PROPERTY))                     {
                        Value[] values = result.getProperty(REFERENCE_PROPERTY).getValues();
                        for(Value v: values)                        {
                           array.add(v.getString());
                        }
                     }
                  }
                  else                  {
                     result = actionsNodeReference.addNode(workspace, ACTIONS_REFERENCE);
                     result.setProperty(WORKSPACE_PROPERTY, workspace);
                     actionsNodeReference.save();
                  }
               }
               Node actionNode = iter.nextNode();
               if(actionNode.canAddMixin(MIX_REFERENCEABLE))               {
                  actionNode.addMixin(MIX_REFERENCEABLE);
                  actionNode.save();
               }
               if (!isExist(array, ((ItemImpl)actionNode).getInternalIdentifier()))               {
                  array.add(((ItemImpl)actionNode).getInternalIdentifier());
               }
            }
            if(result != null && ! array.isEmpty())            {
               result.setProperty(REFERENCE_PROPERTY, array.toArray(new String[0]));
               actionsNodeReference.save();
            }

            if (queryManager == null)            {
               session.logout();
               continue;
            }
            session.logout();
         }
      }
      catch (Exception ex)
      {
         if (log.isErrorEnabled())         {
            log.error("An unexpected error occurs when migrating action", ex);
         }
      }
      log.info("End " + this.getClass().getName());
   }

   @Override
   public boolean shouldProceedToUpgrade(String newVersion, String previousVersion)   {
      // --- return true only for the first version of platform
      return VersionComparator.isAfter(newVersion, previousVersion);
   }

   /**
    *
    * @param values
    * @param uuid
    * @return true if reference exist otherwise false
    * @throws Exception
    */
   private boolean isExist(List<String> values, String uuid) throws Exception   {
      for (String value : values)      {
         if (value != null)         {
            if (uuid.equals(value))            {
               return true;
            }
         }
      }
      return false;
   }
}
