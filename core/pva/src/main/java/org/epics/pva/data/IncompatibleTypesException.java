/*
 *
 * Copyright (C) 2023 European Spallation Source ERIC.
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.epics.pva.data;

/**
 * Exception to handle setting values or updates where types of pvAccess data
 * are incompatible. Returns in error message the type of current value and
 * type of update.
 */
public class IncompatibleTypesException extends UpdateValueException {
    public IncompatibleTypesException(PVAData currentType, Object newValue) {
        super("Incompatible types, current " + currentType.formatType() + ", input object " + newValue);
    }
}
