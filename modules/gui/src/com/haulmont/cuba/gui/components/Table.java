/*
 * Copyright (c) 2008 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.

 * Author: Dmitry Abramov
 * Created: 29.12.2008 13:21:13
 * $Id$
 */
package com.haulmont.cuba.gui.components;

import com.haulmont.cuba.gui.data.CollectionDatasource;

import java.util.List;
import java.util.Set;

import org.dom4j.Element;

public interface Table
    extends
        Component, Component.ActionsOwner, Component.Editable, Component.BelongToWindow 
{
    <T> T getSingleSelected();
    Set getSelected();

    List<Column> getColumns();
    void addColumn(Column column);
    void removeColumn(Column column);

    CollectionDatasource getDatasource();
    void setDatasource(CollectionDatasource datasource);

    public class Column implements HasXmlDescriptor, HasCaption {
        protected Object id;
        protected String caption;
        protected boolean editable;

        protected Class type;
        private Element element;

        public Column(Object id) {
            this.id = id;
        }

        public Object getId() {
            return id;
        }

        public String getCaption() {
            return caption;
        }

        public void setCaption(String caption) {
            this.caption = caption;
        }

        public boolean isEditable() {
            return editable;
        }

        public void setEditable(boolean editable) {
            this.editable = editable;
        }

        public Class getType() {
            return type;
        }

        public void setType(Class type) {
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Column column = (Column) o;

            return id.equals(column.id);

        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        public Element getXmlDescriptor() {
            return element;
        }

        public void setXmlDescriptor(Element element) {
            this.element = element;
        }
    }
}
