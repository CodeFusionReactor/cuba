/*
 * Copyright (c) 2008-2016 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.haulmont.cuba.web.widgets.data.util;

import com.haulmont.cuba.web.widgets.data.AggregationContainer;
import com.haulmont.cuba.web.widgets.data.GroupTableContainer;
import com.vaadin.v7.data.util.ContainerOrderedWrapper;

import java.util.Collection;
import java.util.Map;

@SuppressWarnings("deprecation")
public class GroupTableContainerWrapper extends ContainerOrderedWrapper
        implements GroupTableContainer, AggregationContainer {

    protected final GroupTableContainer groupTableContainer;

    public GroupTableContainerWrapper(GroupTableContainer groupTableContainer) {
        super(groupTableContainer);

        this.groupTableContainer = groupTableContainer;
    }

    @Override
    public void groupBy(Object[] properties) {
        groupTableContainer.groupBy(properties);
    }

    @Override
    public boolean isGroup(Object id) {
        return groupTableContainer.isGroup(id);
    }

    @Override
    public Collection<?> rootGroups() {
        return groupTableContainer.rootGroups();
    }

    @Override
    public boolean hasChildren(Object id) {
        return groupTableContainer.hasChildren(id);
    }

    @Override
    public Collection<?> getChildren(Object id) {
        return groupTableContainer.getChildren(id);
    }

    @Override
    public boolean hasGroups() {
        return groupTableContainer.hasGroups();
    }

    @Override
    public Object getGroupProperty(Object itemId) {
        return groupTableContainer.getGroupProperty(itemId);
    }

    @Override
    public Object getGroupPropertyValue(Object itemId) {
        return groupTableContainer.getGroupPropertyValue(itemId);
    }

    @Override
    public Collection<?> getGroupItemIds(Object itemId) {
        return groupTableContainer.getGroupItemIds(itemId);
    }

    @Override
    public int getGroupItemsCount(Object itemId) {
        return groupTableContainer.getGroupItemsCount(itemId);
    }

    @Override
    public Collection<?> getGroupProperties() {
        return groupTableContainer.getGroupProperties();
    }

    @Override
    public void expand(Object id) {
        groupTableContainer.expand(id);
    }

    @Override
    public boolean isExpanded(Object id) {
        return groupTableContainer.isExpanded(id);
    }

    @Override
    public void expandAll() {
        groupTableContainer.expandAll();
    }

    @Override
    public void collapseAll() {
        groupTableContainer.collapseAll();
    }

    @Override
    public void collapse(Object id) {
        groupTableContainer.collapse(id);
    }

    @Override
    public Collection getAggregationPropertyIds() {
        if (groupTableContainer instanceof AggregationContainer) {
            return ((AggregationContainer) groupTableContainer).getAggregationPropertyIds();
        }
        throw new IllegalStateException("Wrapped container is not AggregationContainer: "
                + groupTableContainer.getClass());
    }

    @Override
    public Type getContainerPropertyAggregation(Object propertyId) {
        if (groupTableContainer instanceof AggregationContainer) {
            return ((AggregationContainer) groupTableContainer).getContainerPropertyAggregation(propertyId);
        }
        throw new IllegalStateException("Wrapped container is not AggregationContainer: "
                + groupTableContainer.getClass());
    }

    @Override
    public void addContainerPropertyAggregation(Object propertyId, Type type) {
        if (groupTableContainer instanceof AggregationContainer) {
            ((AggregationContainer) groupTableContainer).addContainerPropertyAggregation(propertyId, type);
        } else {
            throw new IllegalStateException("Wrapped container is not AggregationContainer: "
                    + groupTableContainer.getClass());
        }
    }

    @Override
    public void removeContainerPropertyAggregation(Object propertyId) {
        if (groupTableContainer instanceof AggregationContainer) {
            ((AggregationContainer) groupTableContainer).removeContainerPropertyAggregation(propertyId);
        } else {
            throw new IllegalStateException("Wrapped container is not AggregationContainer: "
                    + groupTableContainer.getClass());
        }
    }

    @Override
    public Map<Object, Object> aggregate(Context context) {
        if (groupTableContainer instanceof AggregationContainer) {
            return ((AggregationContainer) groupTableContainer).aggregate(context);
        }
        throw new IllegalStateException("Wrapped container is not AggregationContainer: "
                + groupTableContainer.getClass());
    }

    @Override
    public void sort(Object[] propertyId, boolean[] ascending) {
        groupTableContainer.sort(propertyId, ascending);
    }

    @Override
    public Collection<?> getSortableContainerPropertyIds() {
        return groupTableContainer.getSortableContainerPropertyIds();
    }

    @Override
    public void resetSortOrder() {
        groupTableContainer.resetSortOrder();
    }
}