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
import com.haulmont.chile.core.model.MetaPropertyPath;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.global.Messages;
import com.haulmont.cuba.core.global.UserSessionSource;
import com.haulmont.cuba.gui.TestIdManager;
import com.haulmont.cuba.gui.components.ChildEditableController;
import com.haulmont.cuba.gui.components.DateField;
import com.haulmont.cuba.gui.components.Frame;
import com.haulmont.cuba.gui.components.ValidationException;
import com.haulmont.cuba.gui.components.data.ConversionException;
import com.haulmont.cuba.gui.components.data.DataAwareComponentsTools;
import com.haulmont.cuba.gui.components.data.EntityValueSource;
import com.haulmont.cuba.gui.components.data.ValueSource;
import com.haulmont.cuba.gui.components.data.value.DatasourceValueSource;
import com.haulmont.cuba.gui.theme.ThemeConstants;
import com.haulmont.cuba.security.global.UserSession;
import com.haulmont.cuba.web.App;
import com.haulmont.cuba.web.AppUI;
import com.haulmont.cuba.web.widgets.CubaDateField;
import com.vaadin.data.HasValue;
import com.vaadin.shared.ui.datefield.DateTimeResolution;
import com.vaadin.ui.Layout;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Consumer;

@SuppressWarnings("WeakerAccess") // TODO: gg, remove
public class WebDateField<V extends Date> extends WebAbstractViewComponent<Layout, LocalDateTime, V>
        implements DateField<V> {

    protected Resolution resolution;

    protected boolean updatingInstance;

    protected CubaDateField dateField;
    protected WebTimeField timeField;

    protected String dateTimeFormat;
    protected String dateFormat;
    protected String timeFormat;

    protected TimeZone timeZone;

    // TODO: gg, replace
    protected UserSession userSession;

    //    protected boolean buffered = false;
    protected boolean updateTimeFieldResolution = false;

    protected boolean editable = true;

    protected ThemeConstants theme;

    public WebDateField() {
        component = new com.vaadin.ui.CssLayout();
        component.setPrimaryStyleName("c-datefield-layout");

        if (App.isBound()) {
            theme = App.getInstance().getThemeConstants();
        }

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
    }


    protected HasValue.ValueChangeListener<LocalDateTime> createDateValueChangeListener() {
        return e -> {
            componentValueChanged(e.isUserOriginated());

            // TODO: gg, do we need this here?
            // Repaint error state
            component.markAsDirty();
        };
    }

    protected ValueChangeListener createTimeValueChangeListener() {
        return event -> {
            if (!updateTimeFieldResolution) {
                componentValueChanged(true);
            }
        };
    }

    protected void componentValueChanged(boolean isUserOriginated) {
        if (isUserOriginated) {
            V value;

            try {
                value = constructModelValue();

                if (!checkRange(value)) {
                    return;
                }

                LocalDateTime presentationValue = convertToPresentation(value);

                // always update presentation value after change
                // for instance: "1000" entered by user could be "1 000" in case of integer formatting
                setValueToPresentation(presentationValue);
            } catch (ConversionException ce) {
                LoggerFactory.getLogger(getClass()).trace("Unable to convert presentation value to model", ce);

                setValidationError(ce.getLocalizedMessage());
                return;
            }

            V oldValue = internalValue;
            internalValue = value;

            if (!fieldValueEquals(value, oldValue)) {
                ValueChangeEvent event = new ValueChangeEvent(this, oldValue, value); // todo isUserOriginated
                getEventRouter().fireEvent(ValueChangeListener.class, ValueChangeListener::valueChanged, event);
            }
        }
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

        setValueToPresentation(DateTimeUtils.asLocalDateTime(internalValue));
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
            setValueToPresentation(DateTimeUtils.asLocalDateTime(value));
        }
    }

    protected void updateLayout() {
        component.removeAllComponents();
        component.addComponent(dateField);

        if (resolution.ordinal() < Resolution.DAY.ordinal()) {
            component.addComponent(timeField.<com.vaadin.ui.Component>getComponent());
            component.addStyleName("c-datefield-withtime");
        } else {
            component.removeStyleName("c-datefield-withtime");
        }
    }

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
            dateField.setResolution(WebComponentsHelper.convertDateTimeResolution(resolution));
        }
    }

    @Override
    protected void setValueToPresentation(LocalDateTime value) {
        updatingInstance = true;
        try {
            dateField.setValue(value);
            if (value == null) {
                timeField.setValue(null);
            } else {
                timeField.setValue(DateTimeUtils.asDate(value.toLocalTime()));
            }
        } finally {
            updatingInstance = false;
        }
    }

    @SuppressWarnings("unchecked")
    protected V constructModelValue() {
        LocalDateTime dateFieldValue = dateField.getValue();
        if (dateFieldValue == null) {
            return null;
        }

        LocalDate dateValue = dateFieldValue.toLocalDate();
        LocalTime timeValue = timeField.getValue() != null
                ? DateTimeUtils.asLocalTime(timeField.getValue())
                : LocalTime.MIN;
        LocalDateTime resultDateTime = LocalDateTime.of(dateValue, timeValue);

        Date resultDate = DateTimeUtils.asDate(resultDateTime);

        ValueSource<V> valueSource = getValueSource();
        if (valueSource instanceof EntityValueSource) {
            MetaPropertyPath metaPropertyPath = ((DatasourceValueSource) valueSource).getMetaPropertyPath();
            MetaProperty metaProperty = metaPropertyPath.getMetaProperty();
            if (metaProperty != null) {
                Class javaClass = metaProperty.getRange().asDatatype().getJavaClass();
                if (javaClass.equals(java.sql.Date.class)) {
                    return (V) new java.sql.Date(resultDate.getTime());
                }
            }
        }

        return (V) resultDate;
    }

    @Override
    protected LocalDateTime convertToPresentation(V modelValue) throws ConversionException {
        return DateTimeUtils.asLocalDateTime(modelValue);
    }

    @Override
    public void commit() {
        // VAADIN8: gg, implement
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
//        super.commit();
    }

    @Override
    public void discard() {
        // VAADIN8: gg, implement
        /*if (getDatasource() != null && getDatasource().getItem() != null) {
            Date value = getEntityValue(getDatasource().getItem());
            setValueToFields(value);
            fireValueChanged(value);
        }*/
    }

    @Override
    public boolean isBuffered() {
        // VAADIN8: gg, implement
        return false;
    }

    @Override
    public void setBuffered(boolean buffered) {
        // VAADIN8: gg, implement
//        this.buffered = buffered;
    }

    @Override
    public boolean isModified() {
        // VAADIN8: gg, implement
//        return dateField.isModified();
        return false;
    }

    protected void setModified(boolean modified) {
        // VAADIN8: gg,
//        dateField.setModified(modified);
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

    @Override
    public boolean isEditable() {
        return editable;
    }

    @Override
    public void setEditable(boolean editable) {
        if (this.editable == editable) {
            return;
        }

        this.editable = editable;

        boolean parentEditable = true;
        if (parent instanceof ChildEditableController) {
            parentEditable = ((ChildEditableController) parent).isEditable();
        }
        boolean finalEditable = parentEditable && editable;

        setEditableToComponent(finalEditable);
    }

    protected void setEditableToComponent(boolean editable) {
        timeField.setEditable(editable);
        dateField.setReadOnly(!editable);
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


    @Override
    public void requestFocus() {
        dateField.focus();
    }

    @Override
    public void setWidth(String width) {
        super.setWidth(width);

        float componentWidth = getWidth();
        if (componentWidth < 0) {
            String defaultDateFieldWidth = "-1px";
            if (theme != null) {
                defaultDateFieldWidth = theme.get("cuba.web.WebDateField.defaultDateWidth");
            }
            dateField.setWidth(defaultDateFieldWidth);
        } else {
            dateField.setWidth("100%");
        }
    }

    @Override
    public void setHeight(String height) {
        super.setHeight(height);

        // FIXME: gg, do we need any changes?
    }

    @Override
    public boolean isRequired() {
        return false;
    }

    @Override
    public void setRequired(boolean required) {

    }

    @Override
    public String getRequiredMessage() {
        return null;
    }

    @Override
    public void setRequiredMessage(String msg) {

    }

    @Override
    public void addValidator(Validator validator) {

    }

    @Override
    public void removeValidator(Validator validator) {

    }

    @Override
    public Collection<Validator> getValidators() {
        return null;
    }

    @Override
    public String getContextHelpText() {
        return null;
    }

    @Override
    public void setContextHelpText(String contextHelpText) {

    }

    @Override
    public boolean isContextHelpTextHtmlEnabled() {
        return false;
    }

    @Override
    public void setContextHelpTextHtmlEnabled(boolean enabled) {

    }

    @Override
    public Consumer<ContextHelpIconClickEvent> getContextHelpIconClickHandler() {
        return null;
    }

    @Override
    public void setContextHelpIconClickHandler(Consumer<ContextHelpIconClickEvent> handler) {

    }

    @Override
    public boolean isValid() {
        return false;
    }

    @Override
    public void validate() throws ValidationException {

    }
}