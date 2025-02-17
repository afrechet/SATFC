package ca.ubc.cs.beta.fcc.simulator.parameters;

import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.participation.IParticipationDecider;
import ca.ubc.cs.beta.fcc.simulator.participation.OpeningOffPriceHigherThanPrivateValue;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.solver.DistributedFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.LocalFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.SATFCProblemSpecGeneratorImpl;
import ca.ubc.cs.beta.fcc.simulator.state.IStateSaver;
import ca.ubc.cs.beta.fcc.simulator.state.SaveStateToFile;
import ca.ubc.cs.beta.fcc.simulator.station.*;
import ca.ubc.cs.beta.fcc.simulator.unconstrained.ISimulatorUnconstrainedChecker;
import ca.ubc.cs.beta.fcc.simulator.unconstrained.NeverUnconstrainedStationChecker;
import ca.ubc.cs.beta.fcc.simulator.unconstrained.SimulatorUnconstrainedCheckerImpl;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.BandHelper;
import ca.ubc.cs.beta.fcc.simulator.utils.RandomUtils;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.fcc.simulator.valuations.PopValueModel;
import ca.ubc.cs.beta.fcc.simulator.valuations.PopValueModel2;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.parameters.SATFCFacadeParameters;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.DataManager;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies.AddNeighbourLayerStrategy;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import com.google.common.io.Files;
import lombok.*;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.random.RandomGenerator;
import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils.readCSV;

/**
 * Created by newmanne on 2016-05-20.
 */
@UsageTextField(title = "Simulator Parameters", description = "Simulator Parameters")
public class SimulatorParameters extends AbstractOptions {

    private static Logger log;

    public static String getInternalFile(String filename) {
        try {
            File f = new File(SimulatorParameters.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            String jarPath = f.getCanonicalPath();
            return new File(jarPath).getParentFile().getParentFile().getCanonicalPath() + File.separator + "simulator_data" + File.separator + filename;
        } catch (URISyntaxException | IOException e) {
            throw new IllegalStateException();
        }
    }

    @Parameter(names = "-INFO-FILE", description = "csv file with headers FacID,Call,Country,Channel,City,Lat,Lon,Population,DMA,Eligible")
    private String infoFile;

    public String getInfoFile() {
        return infoFile != null ? infoFile : getInternalFile("station_info.csv");
    }

    @Parameter(names = "-VOLUMES-FILE", description = "volumes csv file headers FacID, Volume")
    private String volumeFile;

    public String getVolumeFile() {
        return volumeFile != null ? volumeFile : getInternalFile("volumes.csv");
    }

    @Getter
    @Parameter(names = "-NOT-PARTICIPATING")
    public List<Integer> notParticipating = new ArrayList<>();

    @Getter
    @Parameter(names = "-VALUES-SEED", description = "values file")
    private int valuesSeed = 1;

    @Getter
    @Parameter(names = "-POP-VALUES", description = "Base valuations on population (public model)")
    private boolean popValues = false;

    @Getter
    @Parameter(names = "-INFER-VALUES", description = "infer values for stations not in maxcfstick file")
    private boolean inferValues = true;

    // This file is private and cannot be included in the repo
    @Getter
    @Parameter(names = "-MAX-CF-STICK-FILE", description = "maxcfstick")
    private String maxCFStickFile = System.getenv("SATFC_VALUE_FILE");

    @Parameter(names = "-POP-VALUE-FILE", description = "population value file")
    private String popValueFile;

    public String getPopValueFile() {
        return popValueFile != null ? popValueFile : getInternalFile("popValues.csv");
    }

    @Getter
    @Parameter(names = "-VALUE-FILE", description = "CSV file with station value in each band for each American station (FacID, UHFValue, HVHFValue, LVHFValue)")
    private String valueFile;

    @Parameter(names = "-COMMERCIAL-FILE", description = "CSV file with whether eligible stations are commercial non-commercial (FacID, Commercial)")
    private String commercialFile;

    public String getCommercialFile() {
        return commercialFile != null ? commercialFile : getInternalFile("commercial.csv");
    }

    @Getter
    @Parameter(names = "-STARTING-ASSIGNMENT-FILE", description = "CSV file with columns Ch, FacID specifying a starting assignment for non-participating stations")
    private String startingAssignmentFile;

    @Getter
    @Parameter(names = "-SEND-QUEUE", description = "queue name to send work on")
    private String sendQueue = "send";
    @Getter
    @Parameter(names = "-LISTEN-QUEUE", description = "queue name to listen for work on")
    private String listenQueue = "listen";

    @Getter
    @Parameter(names = "-UHF-ONLY", description = "Ignore non-UHF stations")
    private boolean uhfOnly = false;

    public final static double FCC_UHF_TO_OFF = 900;

    @Getter
    @Setter
    @Parameter(names = "-UHF-TO-OFF", description = "Price per unit volume if a UHF station moves to OFF")
    private double UHFToOff = FCC_UHF_TO_OFF;

    @Getter
    @Parameter(names = "-INCLUDE-VHF", description = "Include the VHF bands in the auctions")
    private boolean includeVHFBands = true;

    @Getter
    @Parameter(names = "-IGNORE-CANADA")
    private boolean ignoreCanada = false;

    @Getter
    @Parameter(names = "-MAX-CHANNEL", description = "highest available channel for the first stage", required = true)
    private int maxChannel;

    @Getter
    @Parameter(names = "-MAX-CHANNEL-FINAL", description = "highest available channel in the last stage")
    private Integer maxChannelFinal = null;

    @Getter
    @Parameter(names = "-SKIP-STAGE", description = "list of stages to skip")
    private Set<Integer> skipStages = new HashSet<>();

    @Getter
    @Parameter(names = "-REMOVE-STATION", description = "Stations to skip")
    private Set<Integer> removeStations = new HashSet<>();

    @Getter
    @Parameter(names = "-CONSTRAINT-SET", description = "constraint set name (not full path!)")
    private String constraintSet = "nov2015";

    @Getter
    @Parameter(names = "-RESTORE-SIMULATION", description = "Restore simulation from state folder")
    private boolean restore = false;

    @Getter
    @Parameter(names = "-START-CITY", description = "City to start from")
    private String city;
    @Getter
    @Parameter(names = "-START-DMA", description = "DMA to start from")
    private String dma;


    @Getter
    @Parameter(names = "-CITY-LINKS", description = "Number of links away from start city")
    private int nLinks = 0;

    @Getter
    @Parameter(names = "-SIMULATOR-OUTPUT-FOLDER", description = "output file name")
    private String outputFolder = "output";

    @Parameter(names = "-SOLVER-TYPE", description = "Type of solver")
    private SolverType solverType = SolverType.LOCAL;

    @Getter
    @Parameter(names = "-PARTICIPATION-MODEL", description = "Type of solver")
    private ParticipationModel participationModel = ParticipationModel.PRICE_HIGHER_THAN_VALUE;

    @Parameter(names = "-UNCONSTRAINED-CHECKER", description = "Type of unconstrained checker")
    private UnconstrainedChecker unconstrainedChecker = UnconstrainedChecker.FCC;

    @Parameter(names = "-UHF-CACHE", description = "If true, cache problems")
    @Getter
    private boolean UHFCache = true;

    @Parameter(names = "-LAZY-UHF-CACHE", description = "If true, do not precompute problems")
    @Getter
    private boolean lazyUHF = true;

    @Getter
    @Parameter(names = "-REVISIT-TIMEOUTS", description = "If true, revisit (UHF) timeout results. False is how it was used in the auction")
    private boolean revisitTimeouts = false;

    @Parameter(names = "-GREEDY-SOLVER-FIRST", description = "If true, always try solving a problem with the greedy solver first")
    @Getter
    private boolean greedyFirst = true;

    @Parameter(names = "-GREEDY-SOLVER-ONLY", description = "If true, don't use SATFC after init")
    @Getter
    private boolean greedyOnly = false;

    public enum BidProcessingAlgorithm {
        FCC, FIRST_TO_FINISH, FIRST_TO_FINISH_SINGLE_PROGRAM, NO_PRICE_DROPS_FOR_TIMEOUTS
    }

    @Getter
    @Parameter(names = "-BID-PROCESSING", description = "Which bid processing algorithm to use")
    private BidProcessingAlgorithm bidProcessingAlgorithm = BidProcessingAlgorithm.FCC;


    @Getter
    @Parameter(names = "-FIRST-TO-FINISH-ROUND-WALLTIME")
    private Double roundWalltime = 1. * 60 * 60;

    @Getter
    @Parameter(names = "-FIRST-TO-FINISH-WORKERS")
    private Integer firstToFinishWorkers;


    @Parameter(names = "-NOISE-STD", description = "Noise to add to 1/3, 2/3")
    @Getter
    private double noiseStd = 0.05;

    @Parameter(names = "-MIP-PARALLELISM", description = "Max threads to run for MIP solving")
    @Getter
    private int parallelism = Runtime.getRuntime().availableProcessors();

    @Parameter(names = "-MIP-CUTOFF", description = "Number of seconds to run initial MIP. In CPU time unless you change the CPLEX params.")
    @Getter
    private double mipCutoff = 60 * 60 * 2;

    @Parameter(names = "-STORE-PROBLEMS", description = "Write every problem to disk")
    @Getter
    private boolean storeProblems = false;

    @Parameter(names = "-FORWARD-AUCTION-AMOUNTS", description = "A list of forward auction amounts, used as a termination condition. In units of billions")
    private List<Double> forwardAuctionAmounts = new ArrayList<>();

    public List<Long> getForwardAuctionAmounts() {
        return forwardAuctionAmounts.stream().map(x -> (long) (x * 1e9)).collect(Collectors.toList());
    }

    @Parameter(names = "-EARLY-STOPPING", description = "Should early stopping be used?")
    @Getter
    private boolean earlyStopping = false;

    @Parameter(names = "-WITHHOLDING-STATIONS-FILE", description = "File listing stations to be withheld")
    @Getter
    private String withholdingStationsFile;

    @Parameter(names = "-STATIONS-TO-USE-FILE", description = "File listing stations that exist in the world")
    @Getter
    private String stationsToUseFile;

    @Getter
    private BidProcessingAlgorithmParameters bidProcessingAlgorithmParameters;

    @Getter
    @ParametersDelegate
    private SATFCFacadeParameters facadeParameters = new SATFCFacadeParameters();

    @Getter
    private HistoricData historicData;

    @Getter
    private PopValueModel2 popValueModel;

    @Getter
    private RandomGenerator valuesGenerator;

    @Getter
    @Parameter(names = "-HVHF-FRAC", description = "How much stations value HVHF as a fraction of UHF")
    private Double HVHFFrac = 2./3;

    @Getter
    @Parameter(names = "-LVHF-FRAC", description = "How much stations value LVHF as a fraction of UHF")
    private Double LVHFFrac = 1./3;

    @Getter
    @Parameter(names = "-LEFT-TAIL", description = "Use a pareto left tail w/ pops value model")
    private Boolean useLeftTail = false;

    @Getter
    @Parameter(names = "-RIGHT-TAIL", description = "Use a pareto right tail w/ pops value model")
    private Boolean useRightTail = true;


    @Getter
    @Parameter(names = "-NEW-MIP", description = "Use the new MIP")
    private Boolean useNewMIP = false;

    @Getter
    @Parameter(names = "-SWITCH-FEASIBILITY-AT-BASE", description = "Switch feasibility when the base clock is reached")
    private Boolean switchFeasibility = false;

    @Getter
    @Parameter(names = "-LOCK-VHF-UNTIL-BASE", description = "The auction is UHF only until the base clock")
    private Boolean lockVHFUntilBase = false;

    @Getter
    @Parameter(names = "-RAISE-CLOCK-TO-FULL-PARTICIPATION", description = "Raise the clock until you get full participation")
    private Boolean raiseClockToFullParticipation = false;


    public String getInteferenceFolder() {
        return facadeParameters.fInterferencesFolder != null ? facadeParameters.fInterferencesFolder : getInternalFile("interference_data");
    }

    public ISimulatorUnconstrainedChecker getUnconstrainedChecker(ParticipationRecord participation, ILadder ladder) {
        switch (unconstrainedChecker) {
            case FCC:
                return new SimulatorUnconstrainedCheckerImpl(getConstraintManager(), participation, ladder);
            case BAD:
                return new NeverUnconstrainedStationChecker();
            default:
                throw new IllegalStateException();
        }
    }

    public List<Band> getAuctionBands() {
        return isIncludeVHFBands() ? Arrays.asList(Band.values()) : Arrays.asList(Band.OFF, Band.UHF);
    }

    public enum UnconstrainedChecker {
        FCC, BAD
    }

    public String getStationInfoFolder() {
        return getInteferenceFolder() + File.separator + constraintSet;
    }

    public double getCutoff() {
        return facadeParameters.fInstanceParameters.Cutoff;
    }

    public void setUp() {
        log = LoggerFactory.getLogger(Simulator.class);

        Preconditions.checkState(SimulatorUtils.CLEARING_TARGETS.contains(maxChannel), "Unrecognized start clearing target %s", maxChannel);
        if (maxChannelFinal != null) {
            Preconditions.checkState(maxChannelFinal >= maxChannel, "Auction must end on a higher channel");
            Preconditions.checkState(SimulatorUtils.CLEARING_TARGETS.contains(maxChannelFinal), "Unrecognized final clearing target %s", maxChannelFinal);
        } else {
            maxChannelFinal = maxChannel;
        }

        if (useLeftTail && !popValues) {
            throw new IllegalArgumentException("Left tail requires pop values");
        }

        final BidProcessingAlgorithmParameters.BidProcessingAlgorithmParametersBuilder bidProcessingAlgorithmParametersBuilder = BidProcessingAlgorithmParameters.builder().bidProcessingAlgorithm(getBidProcessingAlgorithm());
        if (getBidProcessingAlgorithm().equals(BidProcessingAlgorithm.FIRST_TO_FINISH_SINGLE_PROGRAM)) {
            bidProcessingAlgorithmParametersBuilder.roundTimer(roundWalltime);
        } else if (getBidProcessingAlgorithm().equals(BidProcessingAlgorithm.FIRST_TO_FINISH)) {
            Preconditions.checkNotNull(firstToFinishWorkers, "First to finish algorithm requires specifying the number of workers!");
            final DistributedFeasibilitySolver distributedFeasibilitySolver = new DistributedFeasibilitySolver(facadeParameters.fRedisParameters.getJedis(), sendQueue, listenQueue, firstToFinishWorkers);
            bidProcessingAlgorithmParametersBuilder.distributedFeasibilitySolver(distributedFeasibilitySolver);
            bidProcessingAlgorithmParametersBuilder.roundTimer(roundWalltime);
            bidProcessingAlgorithmParametersBuilder.executorService(Executors.newScheduledThreadPool(1));
        }
        log.info("Using {} bid processing algorithm", getBidProcessingAlgorithm());
        bidProcessingAlgorithmParameters = bidProcessingAlgorithmParametersBuilder.build();


        RandomUtils.setRandom(facadeParameters.fInstanceParameters.Seed);
        eventBus = new EventBus();
        BandHelper.setUHFChannels(maxChannel);
        final File outputFolder = new File(getOutputFolder());
        if (isRestore()) {
            Preconditions.checkState(outputFolder.exists() && outputFolder.isDirectory(), "Expected to restore state but no state directory found!");
        } else {
            if (outputFolder.exists()) {
                outputFolder.delete();
            }
            outputFolder.mkdirs();
            new File(getStateFolder()).mkdir();
            new File(getProblemFolder()).mkdir();
        }

        dataManager = new DataManager();
        try {
            dataManager.addData(getStationInfoFolder());
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        stationDB = new CSVStationDB(getInfoFile(), getStationManager(), getWithholdingStationsFile());
        historicData = new HistoricData(stationDB);

        valuesGenerator = new JDKRandomGenerator();
        valuesGenerator.setSeed(getValuesSeed());

        if (popValues) {
//            log.info("Initializing pop value model with {}", getPopValueFile());
            popValueModel = new PopValueModel2(valuesGenerator, stationDB, useLeftTail, useRightTail);
        }

        // Assign values early because otherwise you tend to make mistakes with the value seed and different numbers of calls to the generators based on removing and adding stations
        SimulatorUtils.assignValues(this);

//        // TOOD:
//        Map<Station, Integer> stationIntegerMap = icCounts(getStationManager().getDomains(), stationDB, getConstraintManager());
//        try {
//            Files.write(JSONUtils.toString(stationIntegerMap), new File("test.csv"), Charsets.UTF_8);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        log.info("DONE");
//        // TODO:

        final Set<Integer> toRemove = new HashSet<>();
        final Set<IStationInfo> notNeeded = new HashSet<>();
        for (IStationInfo s : stationDB.getStations()) {
            if (!s.isMainland()) {
                // Note that you have marked some stations in non-mainland USA as always ineligibible on your csv file (Hawaii, Alaska, Virgin Islands, Puerto Rico)
                log.info("Station {} is in a non-mainland DMA ({}) and is being removed", s, s.getDMA());
                toRemove.add(s.getId());
            }

            if (s.getNationality().equals(Nationality.US) && !s.isEligible()) {
                notNeeded.add(s);
                toRemove.add(s.getId());
            }
            if (isIgnoreCanada() && s.getNationality().equals(Nationality.CA)) {
                log.info("Station {} is a Canadian station and ignore Canada flag is set to true", s);
                toRemove.add(s.getId());
            } else if ((isUhfOnly() || !isIncludeVHFBands()) && s.getDomain(Band.UHF).isEmpty()) {
                log.info("Station {} has no domain in UHF, removing due to flag", s);
                toRemove.add(s.getId());
            } else if (!isIncludeVHFBands()) {
                // Remove the VHF bands of UHF stations if we are doing a UHF-only auction
                ((IModifiableStationInfo) s).setMinChannel(StationPackingUtils.UHFmin);
            }
        }

        if (!notNeeded.isEmpty()) {
            log.info("The following {} US stations were not offered an opening price, meaning they must be Not Needed and can effectively be ignored. Excluding from auction" + System.lineSeparator() + "{}", notNeeded.size(), notNeeded);
        }
        toRemove.forEach(stationDB::removeStation);
        // WARNING: Understand that clearing target has not been set yet, so this will be using the full constraint graph (I think)
        if (city != null && nLinks >= 0) {
            log.info("City and links");
            new CityAndLinks(city, dma, nLinks, getStationDB(), getConstraintManager()).function();
        }

        // Assign volumes
        log.info("Setting volumes");
        final IVolumeCalculator volumeCalculator = new CSVVolumeCalculator(getVolumeFile());

        final Set<IStationInfo> americanStations = Sets.newHashSet(stationDB.getStations(Nationality.US));
        final Map<Integer, Integer> volumes = volumeCalculator.getVolumes(americanStations);
        for (IStationInfo stationInfo : americanStations) {
            int volume = volumes.get(stationInfo.getId());
            ((IModifiableStationInfo) stationInfo).setVolume(volume);
        }

        // Set stations as commercial or non-commercial
        try {
            final CSVCommercial commercialReader = new CSVCommercial(getCommercialFile());
            final Map<Integer, Boolean> commercialStatus = commercialReader.getCommercialStatus(americanStations);
            for (IStationInfo stationInfo : americanStations) {
                boolean commercial = commercialStatus.get(stationInfo.getId());
                ((IModifiableStationInfo) stationInfo).setCommercial(commercial);
            }
        } catch (RuntimeException e) {
            // continue without setting
        }
    }

    private String getStateFolder() {
        return getOutputFolder() + File.separator + "state";
    }

    public String getProblemFolder() {
        return getOutputFolder() + File.separator + "problems";
    }


    public long getSeed() {
        return facadeParameters.fInstanceParameters.Seed;
    }

    public IStateSaver getStateSaver() {
        return new SaveStateToFile(getStateFolder(), getEventBus());
    }


    public IStationManager getStationManager() {
        try {
            return dataManager.getData(getStationInfoFolder()).getStationManager();
        } catch (FileNotFoundException e) {
            throw new IllegalStateException();
        }
    }

    public IConstraintManager getConstraintManager() {
        try {
            return dataManager.getData(getStationInfoFolder()).getConstraintManager();
        } catch (FileNotFoundException e) {
            throw new IllegalStateException();
        }
    }

    private DataManager dataManager;
    @Getter
    private IStationDB.IModifiableStationDB stationDB;
    @Getter
    private EventBus eventBus;

    public IFeasibilitySolver createSolver() {
        IFeasibilitySolver solver;
        switch (solverType) {
            case LOCAL:
                log.info("Using a local based solver");
                solver = new LocalFeasibilitySolver(facadeParameters);
                break;
            case DISTRIBUTED:
                // TODO: This won't work
                solver = new DistributedFeasibilitySolver(facadeParameters.fRedisParameters.getJedis(), sendQueue, listenQueue, 0);
                break;
            default:
                throw new IllegalStateException();
        }
        return solver;
    }

    public Simulator.ISATFCProblemSpecGenerator createProblemSpecGenerator() {
        return new SATFCProblemSpecGeneratorImpl(getStationInfoFolder(), getCutoff(), getSeed());
    }

    public enum ParticipationModel {
        PRICE_HIGHER_THAN_VALUE,
        UNIFORM,
        HISTORICAL_DATA,
        COIN_FLIP
    }

    public IParticipationDecider getParticipationDecider(IPrices prices) {
        IParticipationDecider decider;
        switch (participationModel) {
            case PRICE_HIGHER_THAN_VALUE:
                decider = new OpeningOffPriceHigherThanPrivateValue(prices);
                break;
//            case COIN_FLIP:
//                final IParticipationDecider tmp = new OpeningOffPriceHigherThanPrivateValue(prices);
//                final PopValueModel popValueModel = getPopValueModel();
//                Preconditions.checkNotNull(popValueModel, "Trying to use pop value model but null!");
//                decider = s -> {
//                    if (tmp.isParticipating(s)) {
//                        return popValueModel.coinFlip(s);
//                    } else {
//                        return false;
//                    }
//                };
//                break;
            case HISTORICAL_DATA:
                decider = s -> {
                    boolean historicallyParticipated = historicData.getHistoricalStations().contains(s);
                    if (historicallyParticipated) {
                        if (!new OpeningOffPriceHigherThanPrivateValue(prices).isParticipating(s)) {
                            throw new IllegalStateException("Station historically participated but value is not high enough!");
                        }
                    }
                    return historicallyParticipated;
                };
                break;
            default:
                throw new IllegalStateException();
        }
        final IParticipationDecider currDecider = decider;
        decider = s -> {
            if (getNotParticipating().contains(s.getId())) {
                return false;
            }
            Boolean retval = s.isParticipating(prices.getOffers(s));
            if (retval != null) {
                return retval;
            }
            return currDecider.isParticipating(s);
        };
        return decider;
    }

    public enum ScoringRule {
        FCC,
    }

    public interface IPredicateFactory {

        Predicate<IStationInfo> create(Map<Integer, IStationInfo> stations);

    }

    @RequiredArgsConstructor
    public static class CityAndLinks {

        private final String city;
        private final String dma;
        private final int links;
        private final IStationDB.IModifiableStationDB stationDB;
        private final IConstraintManager constraintManager;

        public CityAndLinks(String city, int links, IStationDB.IModifiableStationDB stationDB, IConstraintManager constraintManager) {
            this.city = city;
            this.links = links;
            this.stationDB = stationDB;
            this.constraintManager = constraintManager;
            this.dma = null;
        }

        public void function() {
            Preconditions.checkState(city != null || dma != null);

            // Step 1: Construct the interference graph based on stations in the DB and their domains
            final Map<Station, Set<Integer>> domains = stationDB.getStations()
                    .stream()
                    .collect(Collectors.toMap(IStationInfo::toSATFCStation, IStationInfo::getDomain));

            final SimpleGraph<Station, DefaultEdge> constraintGraph = ConstraintGrouper.getConstraintGraph(domains, constraintManager);

            final Set<Station> cityStations = domains.keySet().stream()
                    .filter(s -> city == null || stationDB.getStationById(s.getID()).getCity().equals(city))
                    .filter(s -> dma == null || stationDB.getStationById(s.getID()).getCity().equals(dma))
                    .collect(Collectors.toSet());

            log.info("Found {} stations in city {}", cityStations.size(), city);
            Preconditions.checkState(cityStations.size() > 0, "No stations found in %s", city);

            final Iterator<Set<Station>> stationsToPack = new AddNeighbourLayerStrategy().getStationsToPack(constraintGraph, cityStations).iterator();
            final Set<Station> output = Sets.newHashSet(cityStations);
            for (int i = 0; i < links; i++) {
                if (stationsToPack.hasNext()) {
                    output.addAll(stationsToPack.next());
                } else {
                    log.info("Exhausted all stations");
                }
            }

            log.info("Found {} stations within {} links of {}. Removing all other stations", output.size(), links, city);
            final Set<IStationInfo> toRemove = new HashSet<>();
            for (IStationInfo s : stationDB.getStations()) {
                if (!output.contains(s.toSATFCStation())) {
                    toRemove.add(s);
                }
            }
            for (IStationInfo s : toRemove) {
                stationDB.removeStation(s.getId());
            }
        }

    }

    public interface IVolumeCalculator {

        Map<Integer, Integer> getVolumes(Set<IStationInfo> stations);

    }

    public static class CSVVolumeCalculator implements IVolumeCalculator {

        final ImmutableMap<Integer, Integer> volumes;

        public CSVVolumeCalculator(String volumeFile) {
            log.info("Reading volumes from {}", volumeFile);
            // Parse volumes
            final ImmutableMap.Builder<Integer, Integer> volumeBuilder = ImmutableMap.builder();
            final Iterable<CSVRecord> volumeRecords = readCSV(volumeFile);
            for (CSVRecord record : volumeRecords) {
                int id = Integer.parseInt(record.get("FacID"));
                int volume = Integer.parseInt(record.get("Volume"));
                volumeBuilder.put(id, volume);
            }
            volumes = volumeBuilder.build();
            log.info("Finished reading volumes");
        }

        @Override
        public Map<Integer, Integer> getVolumes(Set<IStationInfo> stations) {
            return volumes;
        }
    }

    public static class CSVCommercial {

        final ImmutableMap<Integer, Boolean> commerical;

        public CSVCommercial(String commercialFile) {
            log.info("Reading commercial status from {}", commercialFile);
            final ImmutableMap.Builder<Integer, Boolean> commericalBuilder = ImmutableMap.builder();
            final Iterable<CSVRecord> commercialRecords = readCSV(commercialFile);
            for (CSVRecord record : commercialRecords) {
                int id = Integer.parseInt(record.get("FacID"));
                boolean isCommerical = Boolean.parseBoolean(record.get("Commercial"));
                commericalBuilder.put(id, isCommerical);
            }
            commerical = commericalBuilder.build();
            log.info("Finished reading commercial status");
        }

        public Map<Integer, Boolean> getCommercialStatus(Set<IStationInfo> stations) {
            return commerical;
        }


    }

    @RequiredArgsConstructor
    public static class NormalizingVolumeDecorator implements IVolumeCalculator {

        private final IVolumeCalculator decorated;

        @Override
        public Map<Integer, Integer> getVolumes(Set<IStationInfo> stations) {
            final Map<Integer, Integer> volumes = decorated.getVolumes(stations);
            final Map<Integer, Integer> normalized = new HashMap<>();

            double max = volumes.values().stream().mapToDouble(x -> x).max().getAsDouble();
            for (Map.Entry<Integer, Integer> entry : volumes.entrySet()) {
                normalized.put(entry.getKey(), (int) Math.round((entry.getValue() / max) * 1e6));
            }
            return normalized;
        }
    }

    public Map<Integer, Integer> getStartingAssignment() {
        final String sFile = getStartingAssignmentFile();
        final Map<Integer, Integer> assignment = new HashMap<>();
        if (sFile != null) {
            for (CSVRecord record : readCSV(sFile)) {
                final int facID = Integer.parseInt(record.get("FacID"));
                final int chan = Integer.parseInt(record.get("Ch"));
                assignment.put(facID, chan);
            }
        }
        return assignment;
    }

    @Builder
    @Data
    public static class BidProcessingAlgorithmParameters {
        BidProcessingAlgorithm bidProcessingAlgorithm;
        DistributedFeasibilitySolver distributedFeasibilitySolver;
        double roundTimer;
        ScheduledExecutorService executorService;
    }


    public static Map<Station, Integer> icCounts(Map<Station, Set<Integer>> domains, IStationDB stationDB, IConstraintManager constraintManager) {
        final SimpleGraph<Station, DefaultEdge> constraintGraph = ConstraintGrouper.getConstraintGraph(domains, constraintManager);
        final NeighborIndex<Station, DefaultEdge> neighborIndex = new NeighborIndex<>(constraintGraph);
        final Map<Station, Integer> icMap = new HashMap<>();

        final Set<Station> stations = new HashSet<>(domains.keySet());

        for (Station station : stations) {
            if (stationDB.getStationById(station.getID()).getNationality().equals(Nationality.CA)) {
                continue;
            }

            double icNum = 0;

            final Set<Station> neighbours = neighborIndex.neighborsOf(station);
            for (final Station neighbour : neighbours) {
                boolean canNeighbour = stationDB.getStationById(neighbour.getID()).getNationality().equals(Nationality.CA);

                int overallChanMax = 0;
                for (int chanA : domains.get(station)) {
                    int chanMax = 0;
                    for (int chanB : domains.get(neighbour)) {
                        if (!constraintManager.isSatisfyingAssignment(station, chanA, neighbour, chanB)) {
                            chanMax += 1;
                        }
                    }
                    overallChanMax = Math.max(overallChanMax, chanMax);
                }
                icNum += canNeighbour ? overallChanMax * 2.3 : overallChanMax;
            }

            icMap.put(station, (int) Math.round(icNum));
            log.info("{} has {}", station, icNum);
        }
        return icMap;
    }


    public static class HistoricData {

        @Getter
        final Set<IStationInfo> historicalStations = new HashSet<>();
        @Getter
        final Map<Integer, Integer> historicalOpeningPrices = new HashMap<>();

        public HistoricData(IStationDB stationDB) {
            Iterable<CSVRecord> historicalStationsCSV = readCSV(SimulatorParameters.getInternalFile("actual_data/reverse-stations.csv"));
            for (CSVRecord record : historicalStationsCSV) {
                int facId = Integer.parseInt(record.get("facility_id"));
                int historicOpeningPrice = Integer.parseInt(record.get("off_air_opening_price"));
                boolean offAirAllowed = record.get("off_air_option").equals("Y");
                boolean lvhfAllowed = record.get("lvhf_option").equals("Y");
                boolean hvhfAllowed = record.get("hvhf_option").equals("Y");
                // TODO: What if off air isn't allowed?

                historicalOpeningPrices.put(facId, historicOpeningPrice);
                historicalStations.add(stationDB.getStationById(facId));
            }

        }

    }
}