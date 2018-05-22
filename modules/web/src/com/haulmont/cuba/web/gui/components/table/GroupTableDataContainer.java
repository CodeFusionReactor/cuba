/*
 * Copyright (c) 2008-2018 Haulmont.
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
 */

package com.haulmont.cuba.web.gui.components.table;

import com.haulmont.cuba.gui.components.data.GroupTableSource;
import com.haulmont.cuba.gui.data.GroupInfo;
import com.haulmont.cuba.web.widgets.data.GroupTableContainer;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

// todo WORK HERE
public class GroupTableDataContainer<I> extends SortableDataContainer<I> implements GroupTableContainer {

    protected Set<GroupInfo> expandedGroups = new HashSet<>();

    public GroupTableDataContainer(GroupTableSource<I> tableSource,
                                   TableSourceEventsDelegate<I> dataEventsDelegate) {
        super(tableSource, dataEventsDelegate);
    }

    protected GroupTableSource<I> getGroupTableSource() {
        return (GroupTableSource<I>) tableSource;
    }

    @Override
    public void groupBy(Object[] properties) {
        getGroupTableSource().groupBy(properties);
    }

    @Override
    public boolean isGroup(Object id) {
        return id instanceof GroupInfo && getGroupTableSource().containsGroup((GroupInfo) id);
    }

    @Override
    public Collection<?> rootGroups() {
        return getGroupTableSource().rootGroups();
    }

    @Override
    public boolean hasChildren(Object id) {
        return isGroup(id) && getGroupTableSource().hasChildren((GroupInfo) id);
    }

    @Override
    public Collection<?> getChildren(Object id) {
        if (isGroup(id)) {
            return getGroupTableSource().getChildren((GroupInfo) id);
        }
        return Collections.emptyList();
    }

    @Override
    public Object getGroupProperty(Object id) {
        if (isGroup(id)) {
            return getGroupTableSource().getGroupProperty((GroupInfo) id);
        }
        return null;
    }

    @Override
    public Object getGroupPropertyValue(Object id) {
        if (isGroup(id)) {
            return getGroupTableSource().getGroupPropertyValue((GroupInfo) id);
        }
        return null;
    }

    @Override
    public Collection<?> getGroupItemIds(Object id) {
        if (isGroup(id)) {
            return getGroupTableSource().getGroupItemIds((GroupInfo) id);
        }
        return Collections.emptyList();
    }

    @Override
    public int getGroupItemsCount(Object id) {
        if (isGroup(id)) {
            return getGroupTableSource().getGroupItemsCount((GroupInfo) id);
        }
        return 0;
    }

    @Override
    public boolean hasGroups() {
        return getGroupTableSource().hasGroups();
    }

    @Override
    public Collection<?> getGroupProperties() {
        if (hasGroups()) {
            return getGroupTableSource().getGroupProperties();
        }
        return Collections.emptyList();
    }

    @Override
    public void expandAll() {

    }

    @Override
    public void expand(Object id) {

    }

    @Override
    public void collapseAll() {

    }

    @Override
    public void collapse(Object id) {

    }

    @Override
    public boolean isExpanded(Object id) {
        return false;
    }
}