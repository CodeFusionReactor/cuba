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
package com.haulmont.cuba.web.gui.components;

import com.haulmont.bali.util.DateTimeUtils;
import com.haulmont.chile.core.datatypes.Datatypes;
import com.haulmont.chile.core.model.MetaProperty;
import com.haulmont.chile.core.model.utils.InstanceUtils;
import com.haulmont.cuba.core.global.*;
import com.haulmont.cuba.gui.TestIdManager;
import com.haulmont.cuba.gui.components.DateField;
import com.haulmont.cuba.gui.components.Frame;
import com.haulmont.cuba.gui.components.data.ConversionException;
import com.haulmont.cuba.gui.components.data.DataAwareComponentsTools;
import com.haulmont.cuba.gui.components.data.EntityValueSource;
import com.haulmont.cuba.gui.components.data.ValueSource;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.gui.data.impl.WeakItemChangeListener;
import com.haulmont.cuba.gui.data.impl.WeakItemPropertyChangeListener;
import com.haulmont.cuba.security.global.UserSession;
import com.haulmont.cuba.web.AppUI;
import com.haulmont.cuba.web.widgets.CubaDateField;
import com.vaadin.data.HasValue;
import com.vaadin.shared.ui.datefield.DateTimeResolution;
import com.vaadin.ui.Layout;
import org.apache.commons.lang.StringUtils;


import java.sql.Time;
import java.time.LocalDateTime;
import java.util.*;

public class WebDateField<V extends Date> extends WebV8AbstractField<CubaDateFieldWrapper, LocalDateTime, V>
        implements DateField<V> {

    protected Resolution resolution;

    protected boolean updatingInstance;

    protected CubaDateField dateField;
    protected WebTimeField timeField;

    protected Layout innerLayout;

    protected String dateTimeFormat;
    protected String dateFormat;
    protected String timeFormat;

    protected TimeZone timeZone;

    protected UserSession userSession;

    protected Datasource.ItemPropertyChangeListener itemPropertyChangeListener;
    protected WeakItemPropertyChangeListener weakItemPropertyChangeListener;

    protected Datasource.ItemChangeListener itemChangeListener;
    protected WeakItemChangeListener weakItemChangeListener;

    protected boolean buffered = false;
    protected boolean updateTimeFieldResolution = false;

    public WebDateField() {
        innerLayout = new com.vaadin.ui.CssLayout();
        innerLayout.setPrimaryStyleName("c-datefield-layout");

        dateField = new CubaDateField();

        UserSessionSource sessionSource = AppBeans.get(UserSessionSource.NAME);
        userSession = sessionSource.getUserSession();

        Locale locale = userSession.getLocale();
        dateField.setDateFormat(Datatypes.getFormatStringsNN(locale).getDateFormat());
        dateField.setResolution(DateTimeResolution.DAY);

        timeField = new WebTimeField();

        dateField.addValueChangeListener(createDateValueChangeListener());
        timeField.addValueChangeListener(createTimeValueChangeListener());
        setResolution(Resolution.MIN);

        component = new CubaDateFieldWrapper(this, innerLayout);
    }

    protected HasValue.ValueChangeListener<LocalDateTime> createDateValueChangeListener() {
        return e -> {
            if (!checkRange(constructDate())) {
                return;
            }

            updateInstance();

            if (component != null) {
                // Repaint error state
                component.markAsDirty();
            }
        };
    }

    protected ValueChangeListener createTimeValueChangeListener() {
        return event -> {
            if (!checkRange(constructDate())) {
                return;
            }
            if (!updateTimeFieldResolution) {
                updateInstance();
            }
        };
    }

    protected CubaDateField getDateField() {
        return dateField;
    }

    protected WebTimeField getTimeField() {
        return timeField;
    }

    @Override
    public Resolution getResolution() {
        return resolution;
    }

    @Override
    public void setResolution(Resolution resolution) {
        this.resolution = resolution;
        __setResolution(resolution);
        updateLayout();
    }

    @Override
    public void setRangeStart(Date value) {
        dateField.setRangeStart(DateTimeUtils.asLocalDateTime(value));
    }

    @Override
    public Date getRangeStart() {
        return DateTimeUtils.asDate(dateField.getRangeStart());
    }

    @Override
    public void setRangeEnd(Date value) {
        dateField.setRangeEnd(DateTimeUtils.asLocalDateTime(value));
    }

    @Override
    public Date getRangeEnd() {
        return DateTimeUtils.asDate(dateField.getRangeEnd());
    }

    protected boolean checkRange(Date value) {
        if (updatingInstance) {
            return true;
        }

        if (value != null) {
            Date rangeStart = getRangeStart();
            if (rangeStart != null && value.before(rangeStart)) {
                handleDateOutOfRange(value);
                return false;
            }

            Date rangeEnd = getRangeEnd();
            if (rangeEnd != null && value.after(rangeEnd)) {
                handleDateOutOfRange(value);
                return false;
            }
        }

        return true;
    }

    protected void handleDateOutOfRange(Date value) {
        if (getFrame() != null) {
            Messages messages = AppBeans.get(Messages.NAME);
            getFrame().showNotification(messages.getMainMessage("datePicker.dateOutOfRangeMessage"),
                    Frame.NotificationType.TRAY);
        }

        updatingInstance = true;
        try {
            dateField.setValue(DateTimeUtils.asLocalDateTime(internalValue));
            if (internalValue == null) {
                timeField.setValue(null);
            } else {
                timeField.setValue(DateTimeUtils.extractTime(internalValue));
            }
        } finally {
            updatingInstance = false;
        }
    }

    @Override
    public String getDateFormat() {
        return dateTimeFormat;
    }

    @Override
    public void setDateFormat(String dateFormat) {
        dateTimeFormat = dateFormat;
        StringBuilder date = new StringBuilder(dateFormat);
        StringBuilder time = new StringBuilder(dateFormat);
        int timeStartPos = findTimeStartPos(dateFormat);
        if (timeStartPos >= 0) {
            time.delete(0, timeStartPos);
            date.delete(timeStartPos, dateFormat.length());
            timeFormat = StringUtils.trimToEmpty(time.toString());
            timeField.setFormat(timeFormat);
            setResolution(resolution);
        } else if (resolution.ordinal() < Resolution.DAY.ordinal()) {
            setResolution(Resolution.DAY);
        }

        this.dateFormat = StringUtils.trimToEmpty(date.toString());
        dateField.setDateFormat(this.dateFormat);
    }

    @Override
    public TimeZone getTimeZone() {
        return timeZone;
    }

    @Override
    public void setTimeZone(TimeZone timeZone) {
        TimeZone prevTimeZone = this.timeZone;
        Date value = getValue();
        this.timeZone = timeZone;
        dateField.setZoneId(timeZone.toZoneId());
        if (value != null && !Objects.equals(prevTimeZone, timeZone)) {
            setValueToFields(value);
        }
    }

    public void updateLayout() {
        innerLayout.removeAllComponents();
        innerLayout.addComponent(dateField);

        if (resolution.ordinal() < Resolution.DAY.ordinal()) {
            innerLayout.addComponent(timeField.<com.vaadin.ui.Component>getComponent());
            innerLayout.addStyleName("c-datefield-withtime");
        } else {
            innerLayout.removeStyleName("c-datefield-withtime");
        }
    }

    // VAADIN8: gg,
    /*@Override
    protected void attachListener(CubaDateFieldWrapper component) {
        // do nothing
    }*/

    protected int findTimeStartPos(String dateTimeFormat) {
        List<Integer> positions = new ArrayList<>();

        char[] signs = new char[]{'H', 'h', 'm', 's'};
        for (char sign : signs) {
            int pos = dateTimeFormat.indexOf(sign);
            if (pos > -1) {
                positions.add(pos);
            }
        }
        return positions.isEmpty() ? -1 : Collections.min(positions);
    }

    protected void __setResolution(Resolution resolution) {
        if (resolution.ordinal() < Resolution.DAY.ordinal()) {
            timeField.setResolution(resolution);
            // while changing resolution, timeField loses its value, so we need to set it again
            updateTimeFieldResolution = true;
            LocalDateTime value = dateField.getValue();
            if (value == null) {
                timeField.setValue(null);
            } else {
                timeField.setValue(DateTimeUtils.asDate(value.toLocalTime()));
            }
            updateTimeFieldResolution = false;
        } else {
            dateField.setResolution(WebComponentsHelper.convertDateFieldResolution(resolution));
        }
    }

    // VAADIN8: gg, replace with converters

    /*@SuppressWarnings("unchecked")
    @Override
    public V getValue() {
        return (V) constructDate();
    }

    @Override
    public void setValue(V value) {
        setValueToFields((Date) value);
        updateInstance();
    }*/

    @Override
    protected V convertToModel(LocalDateTime componentRawValue) throws ConversionException {
        // TODO: gg,
        return super.convertToModel(componentRawValue);
    }

    @Override
    protected LocalDateTime convertToPresentation(V modelValue) throws ConversionException {
        // TODO: gg,
        return super.convertToPresentation(modelValue);
    }

    @Override
    public void commit() {
        // VAADIN8: gg,
        /*if (updatingInstance) {
            return;
        }

        updatingInstance = true;
        try {
            if (getDatasource() != null && getMetaPropertyPath() != null) {
                Date value = constructDate();

                if (getDatasource().getItem() != null) {
                    InstanceUtils.setValueEx(getDatasource().getItem(), getMetaPropertyPath().getPath(), value);
                    setModified(false);
                }
            }
        } finally {
            updatingInstance = false;
        }

        Object newValue = getValue();
        fireValueChanged(newValue);*/
        super.commit();
    }

    @Override
    public void discard() {
        // VAADIN8: gg,
        /*if (getDatasource() != null && getDatasource().getItem() != null) {
            Date value = getEntityValue(getDatasource().getItem());
            setValueToFields(value);
            fireValueChanged(value);
        }*/
        super.discard();
    }

    @Override
    public boolean isBuffered() {
        // VAADIN8: gg,
//        return buffered;
        return super.isBuffered();
    }

    @Override
    public void setBuffered(boolean buffered) {
        // VAADIN8: gg,
//        this.buffered = buffered;
        super.setBuffered(buffered);
    }

    @Override
    public boolean isModified() {
        // VAADIN8: gg,
//        return dateField.isModified();
        return super.isModified();
    }

    protected void setModified(boolean modified) {
        // VAADIN8: gg,
//        dateField.setModified(modified);
    }

    protected void setValueToFields(Date value) {
        updatingInstance = true;
        try {
            dateField.setValueIgnoreReadOnly(value);
            if (value == null) {
                timeField.setValue(null);
            } else {
                timeField.setValue(extractTime(value));
            }
        } finally {
            updatingInstance = false;
        }
    }

    @Override
    public void setDebugId(String id) {
        super.setDebugId(id);

        if (id != null) {
            TestIdManager testIdManager = AppUI.getCurrent().getTestIdManager();
            timeField.setDebugId(testIdManager.getTestId(id + "_time"));
            dateField.setId(testIdManager.getTestId(id + "_date"));
        }
    }

    @Override
    public void setId(String id) {
        super.setId(id);

        if (id != null && AppUI.getCurrent().isTestMode()) {
            timeField.setId("timepart");
            dateField.setCubaId("datepart");
        }
    }

    protected void updateInstance() {
        if (updatingInstance) {
            return;
        }

        updatingInstance = true;
        try {
            if (getDatasource() != null && getMetaPropertyPath() != null) {
                Date value = constructDate();

                if (!isBuffered()) {
                    if (getDatasource().getItem() != null) {
                        InstanceUtils.setValueEx(getDatasource().getItem(), getMetaPropertyPath().getPath(), value);
                        // VAADIN8: gg,
//                        setModified(false);
                    }
                } else {
                    // VAADIN8: gg,
//                    setModified(true);
                }
            }
        } finally {
            updatingInstance = false;
        }

        Object newValue = getValue();
        fireValueChanged(newValue);
    }

    @Override
    protected void valueBindingConnected(ValueSource<V> valueSource) {
        super.valueBindingConnected(valueSource);

        if (valueSource instanceof EntityValueSource) {
            DataAwareComponentsTools dataAwareComponentsTools = applicationContext.getBean(DataAwareComponentsTools.class);
            EntityValueSource entityValueSource = (EntityValueSource) valueSource;

            dataAwareComponentsTools.setupDateRange(this, entityValueSource);
            dataAwareComponentsTools.setupDateFormat(this, entityValueSource);
        }
    }

    // VAADIN8: gg, more likely we don't need this
    /*protected void fireValueChanged(Object value) {
        Object oldValue = internalValue;

        if (!Objects.equals(oldValue, value)) {
            internalValue = (V) value;

            if (hasValidationError()) {
                setValidationError(null);
            }

            ValueChangeEvent event = new ValueChangeEvent(this, oldValue, value);
            getEventRouter().fireEvent(ValueChangeListener.class, ValueChangeListener::valueChanged, event);
        }
    }*/

    protected Date constructDate() {
        final Date datePickerDate = dateField.getValue();
        if (datePickerDate == null) {
            return null;
        }

        Calendar dateCalendar = Calendar.getInstance(userSession.getLocale());
        if (timeZone != null) {
            dateCalendar.setTimeZone(timeZone);
        }
        dateCalendar.setTime(datePickerDate);
        if (timeField.getValue() == null) {
            dateCalendar.set(Calendar.HOUR_OF_DAY, 0);
            dateCalendar.set(Calendar.MINUTE, 0);
            dateCalendar.set(Calendar.SECOND, 0);
        } else {
            Calendar timeCalendar = Calendar.getInstance(userSession.getLocale());
            timeCalendar.setTime(timeField.<Date>getValue());

            dateCalendar.set(Calendar.HOUR_OF_DAY, timeCalendar.get(Calendar.HOUR_OF_DAY));
            dateCalendar.set(Calendar.MINUTE, timeCalendar.get(Calendar.MINUTE));
            dateCalendar.set(Calendar.SECOND, timeCalendar.get(Calendar.SECOND));
        }

        Date resultDate = dateCalendar.getTime();

        if (getMetaProperty() != null) {
            Class javaClass = getMetaProperty().getRange().asDatatype().getJavaClass();
            if (javaClass.equals(java.sql.Date.class)) {
                return new java.sql.Date(resultDate.getTime());
            } else if (javaClass.equals(Time.class)) {
                return new Time(resultDate.getTime());
            } else {
                return resultDate;
            }
        } else {
            return resultDate;
        }
    }

    @Override
    protected void setEditableToComponent(boolean editable) {
        timeField.setEditable(editable);
        dateField.setReadOnly(!editable);
        // VAADIN8: gg,
//        component.setCompositionReadOnly(!editable);
    }

    @Override
    public int getTabIndex() {
        return dateField.getTabIndex();
    }

    @Override
    public void setTabIndex(int tabIndex) {
        dateField.setTabIndex(tabIndex);
        timeField.setTabIndex(tabIndex);
    }
}