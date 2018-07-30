/**
 * Copyright 2016, Auctionomics, Alexandre Fréchette, Neil Newman, Kevin Leyton-Brown.
 * <p>
 * This file is part of SATFC.
 * <p>
 * SATFC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * SATFC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with SATFC.  If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * For questions, contact us at:
 * afrechet@cs.ubc.ca
 */
package ca.ubc.cs.beta.stationpacking.execution.problemgenerators;

import java.io.FileNotFoundException;

import ca.ubc.cs.beta.stationpacking.execution.parameters.SATFCFacadeParameters;

/**
 * Created by newmanne on 2016-02-16.
 */
public class CutoffChooserFactory {

    public static ICutoffChooser createFromParameters(SATFCFacadeParameters parameters) {
        if (parameters.fCutoffFile == null) {
            return SATFCFacadeProblem::getCutoff;
        } else {
            try {
                return new FileCutoffChooser(parameters.fCutoffFile, parameters.fInstanceParameters.Cutoff);
            } catch (FileNotFoundException e) {
                throw new IllegalArgumentException("File not found " + parameters.fCutoffFile, e);
            }
        }
    }

}
