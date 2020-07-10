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
package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.yaml;

/**
 * Created by newmanne on 27/10/15.
 */
public enum SolverType {
    CLASP,
    SATENSTEIN,
    SAT_PRESOLVER,
    UNSAT_PRESOLVER,
    UNDERCONSTRAINED,
    CONNECTED_COMPONENTS,
    ARC_CONSISTENCY,
    PYTHON_VERIFIER,
    VERIFIER,
    CACHE,
    SAT_CACHE,
    UNSAT_CACHE,
    PARALLEL,
    RESULT_SAVER,
    CNF,
    CHANNEL_KILLER,
    DELAY,
    TIME_BOUNDED,
    PREVIOUS_ASSIGNMENT,
    UNSAT_LABELLER,
    GREEDY,
    MIP_SAVER, ASP_SAVER, NONE,
    COMMAND_LINE,
    UNSAT_RUNTIME,
    CPLEX,
}
