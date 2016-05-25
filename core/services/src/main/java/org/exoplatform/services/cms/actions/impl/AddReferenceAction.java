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
package org.exoplatform.services.cms.actions.impl;

import java.util.ArrayList;
import java.util.List;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.observation.Event;
import org.apache.commons.chain.Context;
import org.exoplatform.services.command.action.Action;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.impl.core.NodeImpl;

/**
 * Created by The eXo Platform SAS
 * Author : aboughzela@exoplatform.com
 */
public class AddReferenceAction implements Action{
   private static final String MIX_REFERENCEABLE = "mix:referenceable" ;

   private static final String REFERENCE_PROPERTY = "exo:actions";

   private static final String ACTIONS_REFERENCE = "exo:actionsReference";

   private final String WORKSPACE_PROPERTY = "exo:workspace";

   @Override
   public boolean execute(Context context) throws Exception   {
      Node actionNode =null;
      NodeImpl node = (NodeImpl)context.get("currentItem");
      int eventType = (int)context.get("event");
      ManageableRepository jcrRepository = (ManageableRepository) node.getSession().getRepository();
      Node actionHome = ActionServiceContainerImpl.getReferenceActionHome(jcrRepository);
      if(actionHome.hasNode(node.getSession().getWorkspace().getName()))      {
         actionNode = actionHome.getNode(node.getSession().getWorkspace().getName());
      }
      else      {
         actionNode = actionHome.addNode(node.getSession().getWorkspace().getName(), ACTIONS_REFERENCE);
         actionNode.setProperty(WORKSPACE_PROPERTY, node.getSession().getWorkspace().getName());
         actionHome.save();
      }
      if(actionNode != null)      {
         switch (eventType)         {
            case Event.NODE_ADDED : addReference(actionNode, node);
               break;
            case Event.NODE_REMOVED : removeReference(actionNode, node);
            default : break;
         }
      }
      return false;
   }

   private void addReference(Node actionNode, Node currentItem) throws RepositoryException   {
      List<String> array = getValues(actionNode, currentItem);
      array.add(currentItem.getUUID());
      actionNode.setProperty(REFERENCE_PROPERTY, array.toArray(new String[0]));
      actionNode.save();
   }

   private void removeReference(Node actionNode, Node currentItem) throws RepositoryException   {
      List<String> array = getValues(actionNode, currentItem);
      array.remove(currentItem.getUUID());
      actionNode.setProperty(REFERENCE_PROPERTY, array.toArray(new String[0]));
      actionNode.save();
   }

   private List<String> getValues(Node actionNode, Node currentItem) throws RepositoryException   {
      List<String> array = new ArrayList<String>();

      if(currentItem.canAddMixin(MIX_REFERENCEABLE))      {
         currentItem.addMixin(MIX_REFERENCEABLE);
      }
      if (actionNode.hasProperty(REFERENCE_PROPERTY))      {
         Value[] values = actionNode.getProperty(REFERENCE_PROPERTY).getValues();
         for(Value v: values)         {
            array.add(v.getString());
         }
      }
      return array;
   }
}
