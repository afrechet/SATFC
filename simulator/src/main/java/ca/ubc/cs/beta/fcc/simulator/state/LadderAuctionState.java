package ca.ubc.cs.beta.fcc.simulator.state;

import ca.ubc.cs.beta.fcc.simulator.catchup.CatchupPoint;
import ca.ubc.cs.beta.fcc.simulator.ladder.IModifiableLadder;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import com.google.common.collect.ImmutableTable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Created by newmanne on 2016-09-27.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LadderAuctionState {

    private IPrices<Double> benchmarkPrices;
    private ParticipationRecord participation;
    private int round;
    private int stage;
    private Map<Integer, Integer> assignment;
    private IModifiableLadder ladder;

    private IPrices<Long> offers;
    private ImmutableTable<IStationInfo, Band, Double> vacancies;
    private ImmutableTable<IStationInfo, Band, Double> reductionCoefficients;
    private List<IStationInfo> bidProcessingOrder;

    // The current compensation of every station
    private Map<IStationInfo, Long> prices;

    // The current catchup point of each station
    private Map<IStationInfo, CatchupPoint> catchupPoints;

    // UHF to off benchmark
    private double baseClockPrice;

//    // Should the state early terminate
//    private boolean earlyTerminate;

    private long biddingCompensation;
    private long currentlyInfeasibleCompensation;
    private long pendingCatchupCompensation;
    private long provisionallyWinningCompensation;

    private boolean earlyStopped;

    public long totalCompensation() {
        return biddingCompensation + currentlyInfeasibleCompensation + pendingCatchupCompensation + provisionallyWinningCompensation;
    }

}
