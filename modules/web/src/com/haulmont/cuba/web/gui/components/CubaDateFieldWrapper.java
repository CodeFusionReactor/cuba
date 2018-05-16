/*
 * Copyright (c) 2008-2017 Haulmont.
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

package com.haulmont.cuba.web.gui.components;

import com.haulmont.cuba.gui.theme.ThemeConstants;
import com.haulmont.cuba.web.App;
import com.vaadin.ui.Component;
import com.vaadin.ui.Layout;

import java.time.LocalDateTime;

public class CubaDateFieldWrapper extends com.vaadin.ui.CustomField<LocalDateTime> {

    protected final Layout composition;
    protected final WebDateField dateField;

    protected ThemeConstants theme;
    protected boolean showBufferedExceptions = false;

    public CubaDateFieldWrapper(WebDateField dateField, Layout composition) {
        this.dateField = dateField;
        this.composition = composition;

        if (App.isBound()) {
            theme = App.getInstance().getThemeConstants();
        }

        setSizeUndefined();
        // VAADIN8: gg,
//        setConverter(new ObjectToObjectConverter());
        // VAADIN8: gg,
//        setValidationVisible(false);
//        setShowBufferedSourceException(false);
//        setShowErrorForDisabledState(false);

//        setFocusDelegate(dateField.getDateField());

        setPrimaryStyleName("c-datefield-composition");
    }

    // VAADIN8: gg,
    /*@Override
    protected Buffered.SourceException getCurrentBufferedSourceException() {
        if (!showBufferedExceptions) {
            return null;
        }

        return super.getCurrentBufferedSourceException();
    }*/

    @Override
    protected Component initContent() {
        return composition;
    }

    public WebDateField getCubaField() {
        return dateField;
    }

    @Override
    protected void doSetValue(LocalDateTime value) {
//        dateField.setValue(value);
    }

    @Override
    public LocalDateTime getValue() {
//        return dateField.getValue();
        return null;
    }

    @Override
    public boolean isEmpty() {
        return getValue() == null;
    }

//    @Override
//    public void focus() {
//        dateField.getDateField().focus();
//    }

    /*@Override
    public void setWidth(float width, Unit unit) {
        super.setWidth(width, unit);

        if (composition != null && dateField != null) {
            if (width < 0) {
                composition.setWidth(-1, Unit.PIXELS);
                String defaultDateFieldWidth = "-1px";
                if (theme != null) {
                    defaultDateFieldWidth = theme.get("cuba.web.WebDateField.defaultDateWidth");
                }
                dateField.getDateField().setWidth(defaultDateFieldWidth);
            } else {
                composition.setWidth(100, Unit.PERCENTAGE);
                dateField.getDateField().setWidth("100%");
            }
        }
    }*/

    @Override
    public void setHeight(float height, Unit unit) {
        super.setHeight(height, unit);

        if (composition != null) {
            if (height < 0) {
                composition.setHeight(-1, Unit.PIXELS);
            } else {
                composition.setHeight(100, Unit.PERCENTAGE);
            }
        }
    }

    /*@Override
    public void setReadOnly(boolean readOnly) {
        dateField.setEditable(!readOnly);
    }

    @Override
    public boolean isReadOnly() {
        return !dateField.isEditableWithParent();
    }*/

    /*public void setCompositionReadOnly(boolean readOnly) {
        super.setReadOnly(readOnly);
    }*/

    // VAADIN8: gg,
    /*@Override
    public ErrorMessage getErrorMessage() {
        ErrorMessage superError = super.getErrorMessage();
        if (!isReadOnly() && isRequired() && isEmpty()) {
            ErrorMessage error = AbstractErrorMessage.getErrorMessageForException(
                    new com.vaadin.v7.data.Validator.EmptyValueException(getRequiredError()));
            if (error != null) {
                return new CompositeErrorMessage(superError, error);
            }
        }
        return superError;
    }*/
}