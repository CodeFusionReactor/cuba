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

package com.haulmont.cuba.web.widgets;

import com.haulmont.cuba.web.widgets.client.timefield.CubaTimeFieldState;
import com.haulmont.cuba.web.widgets.client.timefield.TimeResolution;
import com.vaadin.event.FieldEvents;
import com.vaadin.shared.communication.FieldRpc;
import com.vaadin.shared.ui.textfield.AbstractTextFieldServerRpc;
import com.vaadin.ui.AbstractField;
import elemental.json.Json;
import org.apache.commons.lang.StringUtils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CubaTimeField extends AbstractField<LocalTime> {

    protected final class AbstractTextFieldServerRpcImpl implements AbstractTextFieldServerRpc {

        @Override
        public void setText(String text, int cursorPosition) {
            updateDiffstate("text", Json.create(text));

//            lastKnownCursorPosition = cursorPosition;
            LocalTime value = parseValue(text);
            setValue(value, true);
        }
    }

    protected final class AbstractTextFieldFocusAndBlurRpcImpl implements FieldRpc.FocusAndBlurServerRpc {

        @Override
        public void blur() {
            fireEvent(new FieldEvents.BlurEvent(CubaTimeField.this));
        }

        @Override
        public void focus() {
            fireEvent(new FieldEvents.FocusEvent(CubaTimeField.this));
        }
    }


    protected TimeResolution resolution = TimeResolution.MINUTE;
    protected String placeholder;
    protected String timeFormat;

    protected LocalTime value;

    public CubaTimeField() {
        getState(false).maskedMode = true;

        registerRpc(new AbstractTextFieldServerRpcImpl());
        registerRpc(new AbstractTextFieldFocusAndBlurRpcImpl());
    }

    @Override
    protected CubaTimeFieldState getState() {
        return (CubaTimeFieldState) super.getState();
    }

    @Override
    protected CubaTimeFieldState getState(boolean markAsDirty) {
        return (CubaTimeFieldState) super.getState(markAsDirty);
    }


    @Override
    protected void doSetValue(LocalTime value) {
        this.value = value;

        getState().text = formatDate(value);
    }

    @Override
    public LocalTime getValue() {
        return this.value;
    }

    public String getTimeFormat() {
        return this.timeFormat;
    }

    public void setTimeFormat(String timeFormat) {
        // TODO: gg, to do to do do to do to do
        this.timeFormat = timeFormat;

        updateTimeFormat();
    }

    protected void updateTimeFormat() {
        String mask = StringUtils.replaceChars(timeFormat, "Hhmsa", "####U");
        placeholder = StringUtils.replaceChars(mask, "#U", "__");
        getState().mask = mask;
    }

    public TimeResolution getResolution() {
        return resolution;
    }

    public void setResolution(TimeResolution resolution) {
        this.resolution = resolution;
        updateResolution();
    }

    protected void updateResolution() {

        String timeFormat = getResolutionsHigherOrEqualTo(this.resolution)
                .map(this::getResolutionFormat)
                .collect(Collectors.joining(":"));

        this.timeFormat = timeFormat;
        updateTimeFormat();
    }

    protected Stream<TimeResolution> getResolutionsHigherOrEqualTo(TimeResolution resolution) {
        return Stream.of(TimeResolution.values())
                .skip(resolution.ordinal())
                .sorted(Comparator.reverseOrder());
    }

    protected String getResolutionFormat(TimeResolution resolution) {
//        checkNotNull(resolution, "Resolution can't be null");

        switch (resolution) {
            case HOUR:
                return "HH";
            case MINUTE:
                return "mm";
            case SECOND:
                return "ss";
            default:
                throw new IllegalArgumentException("Cannot detect resolution type");
        }
    }

    protected LocalTime parseValue(String text) {
        if (StringUtils.isNotEmpty(text) && !text.equals(placeholder)) {
            DateTimeFormatter dateTimeFormatter = getDateTimeFormatter();
            return LocalTime.parse(text, dateTimeFormatter);
        } else {
            return null;
        }
    }

    protected String formatDate(LocalTime value) {
        if (value == null) {
            return "";
        }

        DateTimeFormatter dateTimeFormatter = getDateTimeFormatter();
        return value.format(dateTimeFormatter);
    }

    protected DateTimeFormatter getDateTimeFormatter() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(timeFormat);
        Locale locale = getLocale();
        if (locale != null) {
            dateTimeFormatter = dateTimeFormatter.withLocale(locale);
        }
        return dateTimeFormatter;
    }
}
