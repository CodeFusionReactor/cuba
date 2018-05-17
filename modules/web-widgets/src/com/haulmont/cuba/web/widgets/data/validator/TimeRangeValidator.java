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

package com.haulmont.cuba.web.widgets.data.validator;

import com.vaadin.data.validator.RangeValidator;

import java.time.LocalTime;
import java.util.Comparator;

/**
 * Validator for validating that a {@link LocalTime} is inside a given range.
 */
public class TimeRangeValidator extends RangeValidator<LocalTime> {
    /**
     * Creates a new range validator of the given type. Passing null to either
     * {@code minValue} or {@code maxValue} means there is no limit in that
     * direction. Both limits may be null; this can be useful if the limits are
     * resolved programmatically. The result of passing null to {@code apply}
     * depends on the given comparator.
     *
     * @param errorMessage the error message to return if validation fails, not null
     * @param minValue     the minimum value to accept or null for no limit
     * @param maxValue     the maximum value to accept or null for no limit
     */
    public TimeRangeValidator(String errorMessage, LocalTime minValue, LocalTime maxValue) {
        super(errorMessage, Comparator.naturalOrder(), minValue, maxValue);
    }
}
