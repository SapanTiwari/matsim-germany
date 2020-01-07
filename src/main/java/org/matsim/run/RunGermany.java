/* *********************************************************************** *
 * project: org.matsim.*
 * EditRoutesTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2019 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */


package org.matsim.run;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.matsim.analysis.TransportPlanningMainModeIdentifier;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ModeParams;
import org.matsim.core.config.groups.QSimConfigGroup.TrafficDynamics;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule.DefaultSelector;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule.DefaultStrategy;
import org.matsim.core.router.AnalysisMainModeIdentifier;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicles;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.ModeMappingForPassengersParameterSet;
import ch.sbb.matsim.routing.pt.raptor.RaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;

import org.matsim.contrib.accessibility.utils.MergeNetworks;


/**
* @author smueller
*/

public class RunGermany {
	
	private static final String inputDir ="../shared-svn/studies/countries/de/matsim-germany/matsim-input/";
	private static final String outputDir = "../shared-svn/studies/countries/de/matsim-germany/matsim-output/";

//	contains all primary, trunk and motorway roads from osm
	private static final String inputNetworkRoads = 		inputDir + "network_osm_secondary.xml.gz";

	//	contains all db ice and ic services from 2016 from gtfs data
	private static final String inputNetworkTrain =		 	inputDir + "network_train_gtfs.xml.gz";
	private static final String inputScheduleTrain =		inputDir + "transit_schedule_train_gtfs.xml.gz";
	private static final String inputVehiclesTrain =		inputDir + "transit_vehicles_train.xml.gz";

//	contains all german plane services from 09/09 from oag data
	private static final String inputNetworkPlane = 		inputDir + "network_plane.xml";
	private static final String inputSchedulePlane =		inputDir + "transit_schedule_plane.xml";
	private static final String inputVehiclesPlane =		inputDir + "transit_vehicles_plane.xml";
	
	private static final String inputPlans = 				inputDir + "populationTrainPlaneBERMUC5.0pct.xml";

	private static final int noOfThreads = 8;

	public static void main(String[] args) {
		
		Config config = ConfigUtils.createConfig();
		
		config.controler().setLastIteration(10);
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setOutputDirectory(outputDir);
		
		config.global().setCoordinateSystem("EPSG:31467");
		config.global().setNumberOfThreads(noOfThreads);
		
		Collection<String> networkModes = new HashSet<>();
		networkModes.add("car");
		config.plansCalcRoute().setNetworkModes(networkModes);
		
//		we are only scoring trips
		config.planCalcScore().addActivityParams(new ActivityParams("origin").setScoringThisActivityAtAll(false));
		config.planCalcScore().addActivityParams(new ActivityParams("shop").setScoringThisActivityAtAll(false));
		config.planCalcScore().addActivityParams(new ActivityParams("business").setScoringThisActivityAtAll(false));
		config.planCalcScore().addActivityParams(new ActivityParams("holiday").setScoringThisActivityAtAll(false));
		config.planCalcScore().addActivityParams(new ActivityParams("work").setScoringThisActivityAtAll(false));
		config.planCalcScore().addActivityParams(new ActivityParams("education").setScoringThisActivityAtAll(false));
		config.planCalcScore().addActivityParams(new ActivityParams("other").setScoringThisActivityAtAll(false));
				
		config.qsim().setStartTime(0);
		config.qsim().setEndTime(36. * 3600);
		config.qsim().setNumberOfThreads(noOfThreads);
		config.qsim().setMainModes(networkModes);
		
		// vsp defaults
		config.vspExperimental().setVspDefaultsCheckingLevel( VspExperimentalConfigGroup.VspDefaultsCheckingLevel.info );
		config.plansCalcRoute().setInsertingAccessEgressWalk( true );
		config.qsim().setUsingTravelTimeCheckInTeleportation( true );
		config.qsim().setTrafficDynamics( TrafficDynamics.kinematicWaves );
		
		config.strategy().setFractionOfIterationsToDisableInnovation(0.8);
//		config.strategy().setMaxAgentPlanMemorySize(3);	
		config.strategy().clearStrategySettings();
		
		StrategySettings stratSetsReRoute = new StrategySettings();
		stratSetsReRoute.setStrategyName(DefaultStrategy.ReRoute);
		stratSetsReRoute.setWeight(0.15);
		
		StrategySettings stratSetsChangeExpBeta = new StrategySettings();
		stratSetsChangeExpBeta.setStrategyName(DefaultSelector.ChangeExpBeta);
		stratSetsChangeExpBeta.setWeight(0.85);
		
		config.strategy().addStrategySettings(stratSetsReRoute);
		config.strategy().addStrategySettings(stratSetsChangeExpBeta);
		
		config.network().setInputFile(inputNetworkRoads);
		
		config.plans().setInputFile(inputPlans);
		
		config.transit().setUseTransit(true);
		
		Set<String> transitModes = new HashSet<>();
//		transitModes.add(TransportMode.train);
//		transitModes.add(TransportMode.airplane);
		transitModes.add("longDistancePt");
		config.transit().setTransitModes(transitModes );

		SwissRailRaptorConfigGroup srrConfig = new SwissRailRaptorConfigGroup();
		srrConfig.setUseModeMappingForPassengers(true);
		
		ModeMappingForPassengersParameterSet modeMappingTrain = new ModeMappingForPassengersParameterSet();
		modeMappingTrain.setPassengerMode(TransportMode.train);
		modeMappingTrain.setRouteMode(TransportMode.train);
		srrConfig.addModeMappingForPassengers(modeMappingTrain);
		
		ModeMappingForPassengersParameterSet modeMappingAirplane = new ModeMappingForPassengersParameterSet();
		modeMappingAirplane.setPassengerMode(TransportMode.airplane);
		modeMappingAirplane.setRouteMode(TransportMode.airplane);
		srrConfig.addModeMappingForPassengers(modeMappingAirplane);
		
		srrConfig.setUseIntermodalAccessEgress(true);
		
		IntermodalAccessEgressParameterSet intermodalAccessEgressParameterSet = new IntermodalAccessEgressParameterSet();
		intermodalAccessEgressParameterSet.setMode("car");
		intermodalAccessEgressParameterSet.setMaxRadius(500 * 1000);
		intermodalAccessEgressParameterSet.setInitialSearchRadius(100 * 1000);
		intermodalAccessEgressParameterSet.setSearchExtensionRadius(150 * 1000);
//		intermodalAccessEgressParameterSet.setStopFilterAttribute("");
		srrConfig.addIntermodalAccessEgress(intermodalAccessEgressParameterSet);
		
		config.addModule(srrConfig);
		
		ModeParams scorePt = config.planCalcScore().getModes().get(TransportMode.pt);
		
		ModeParams scoreTrain = new ModeParams(TransportMode.train);
		scoreTrain.setConstant(scorePt.getConstant());
		scoreTrain.setDailyMonetaryConstant(scorePt.getDailyMonetaryConstant());
		scoreTrain.setDailyUtilityConstant(scorePt.getDailyUtilityConstant());
		scoreTrain.setMarginalUtilityOfDistance(scorePt.getMarginalUtilityOfDistance());
		scoreTrain.setMarginalUtilityOfTraveling(scorePt.getMarginalUtilityOfTraveling());
		scoreTrain.setMonetaryDistanceRate(scorePt.getMonetaryDistanceRate());
		config.planCalcScore().addModeParams(scoreTrain);
		
		ModeParams scoreAirplane = new ModeParams(TransportMode.airplane);
//		scoreAirplane.setConstant(scorePt.getConstant());
		scoreAirplane.setConstant(-12.);
		scoreAirplane.setDailyMonetaryConstant(scorePt.getDailyMonetaryConstant());
		scoreAirplane.setDailyUtilityConstant(scorePt.getDailyUtilityConstant());
		scoreAirplane.setMarginalUtilityOfDistance(scorePt.getMarginalUtilityOfDistance());
		scoreAirplane.setMarginalUtilityOfTraveling(scorePt.getMarginalUtilityOfTraveling());
		scoreAirplane.setMonetaryDistanceRate(scorePt.getMonetaryDistanceRate());
		config.planCalcScore().addModeParams(scoreAirplane);
		
		Scenario scenario = ScenarioUtils.loadScenario(config) ;
		
		Network DBNetwork = NetworkUtils.readNetwork(inputNetworkTrain);
		Set<String> trainModes = new HashSet<>();
		trainModes.add(TransportMode.train);
		DBNetwork.getLinks().values().forEach(l -> l.setAllowedModes(trainModes));
		MergeNetworks.merge(scenario.getNetwork(),"", DBNetwork);
		
		Config trainConfig = ConfigUtils.createConfig();
		trainConfig.transit().setTransitScheduleFile(inputScheduleTrain);
		trainConfig.transit().setVehiclesFile(inputVehiclesTrain);
		Scenario trainScenario = ScenarioUtils.loadScenario(trainConfig);
		trainScenario.getTransitSchedule().getTransitLines().values().forEach(line -> line.getRoutes().values().forEach(route -> route.setTransportMode(TransportMode.train)));
		mergeSchedules(scenario.getTransitSchedule(), trainScenario.getTransitSchedule());
		mergeVehicles(scenario.getTransitVehicles(), trainScenario.getTransitVehicles());
		
		Network airplaneNetwork = NetworkUtils.readNetwork(inputNetworkPlane);
		Set<String> airplaneModes = new HashSet<>();
		airplaneModes.add(TransportMode.airplane);
		airplaneNetwork.getLinks().values().forEach(l -> l.setAllowedModes(airplaneModes));
		MergeNetworks.merge(scenario.getNetwork(),"", airplaneNetwork);
		
		Config airplaneConfig = ConfigUtils.createConfig();
		airplaneConfig.transit().setTransitScheduleFile(inputSchedulePlane);
		airplaneConfig.transit().setVehiclesFile(inputVehiclesPlane);
		Scenario airplaneScenario = ScenarioUtils.loadScenario(airplaneConfig);
		airplaneScenario.getTransitSchedule().getTransitLines().values().forEach(line -> line.getRoutes().values().forEach(route -> route.setTransportMode(TransportMode.airplane)));
		mergeSchedules(scenario.getTransitSchedule(), airplaneScenario.getTransitSchedule());
		mergeVehicles(scenario.getTransitVehicles(), airplaneScenario.getTransitVehicles());
		
		Controler controler = new Controler( scenario ) ;
		controler.addOverridingModule(new SwissRailRaptorModule());
		
		controler.addOverridingModule( new AbstractModule(){
			@Override public void install() {
//				this.bindScoringFunctionFactory().to( MyScoringFunctionFactory.class ) ;
//				install( new SwissRailRaptorModule() );
				bind(RaptorParametersForPerson.class).to(AirplaneTrainSwitcherIndividualRaptorParametersForPerson.class);
//				bind(AnalysisMainModeIdentifier.class).to(MyMainModeIdentifier.class);
			}
		} );
		
		controler.run();

	}
	
	private static void mergeSchedules(TransitSchedule schedule, TransitSchedule toBeMerged) {
		toBeMerged.getFacilities().values().forEach(schedule::addStopFacility);
		toBeMerged.getTransitLines().values().forEach(schedule::addTransitLine);
	}

	private static void mergeVehicles(Vehicles vehicles, Vehicles toBeMerged) {
		toBeMerged.getVehicleTypes().values().forEach(vehicles::addVehicleType);
		toBeMerged.getVehicles().values().forEach(vehicles::addVehicle);
	}

}

class MyScoringFunctionFactory implements ScoringFunctionFactory {

	@Override
	public ScoringFunction createNewScoringFunction(Person person) {
		// TODO Auto-generated method stub
		return new MyScoringFunction();
	}
	
}

class MyScoringFunction implements ScoringFunction {
	
	private double score;

	@Override
	public void handleActivity(Activity activity) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void handleLeg(Leg leg) {
		score -= leg.getTravelTime();
		
		if (leg.getMode().equals(TransportMode.airplane) ) {
			score -= 2 * 3600;
		
		}
		
		
	}

	@Override
	public void agentStuck(double time) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void addMoney(double amount) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void finish() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public double getScore() {
		// TODO Auto-generated method stub
		return score;
	}

	@Override
	public void handleEvent(Event event) {
		// TODO Auto-generated method stub
		
	}
	
}