package org.vopen.android_sdk.obd_service;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.github.pires.obd.commands.ObdCommand;
import com.github.pires.obd.commands.SpeedCommand;
import com.github.pires.obd.commands.control.DistanceMILOnCommand;
import com.github.pires.obd.commands.control.DistanceSinceCCCommand;
import com.github.pires.obd.commands.control.DtcNumberCommand;
import com.github.pires.obd.commands.control.EquivalentRatioCommand;
import com.github.pires.obd.commands.control.IgnitionMonitorCommand;
import com.github.pires.obd.commands.control.ModuleVoltageCommand;
import com.github.pires.obd.commands.control.PendingTroubleCodesCommand;
import com.github.pires.obd.commands.control.PermanentTroubleCodesCommand;
import com.github.pires.obd.commands.control.TimingAdvanceCommand;
import com.github.pires.obd.commands.control.TroubleCodesCommand;
import com.github.pires.obd.commands.control.VinCommand;
import com.github.pires.obd.commands.engine.AbsoluteLoadCommand;
import com.github.pires.obd.commands.engine.LoadCommand;
import com.github.pires.obd.commands.engine.OilTempCommand;
import com.github.pires.obd.commands.engine.RPMCommand;
import com.github.pires.obd.commands.engine.RuntimeCommand;
import com.github.pires.obd.commands.engine.MassAirFlowCommand;
import com.github.pires.obd.commands.engine.ThrottlePositionCommand;
import com.github.pires.obd.commands.fuel.AirFuelRatioCommand;
import com.github.pires.obd.commands.fuel.ConsumptionRateCommand;
import com.github.pires.obd.commands.fuel.FindFuelTypeCommand;
import com.github.pires.obd.commands.fuel.FuelLevelCommand;
import com.github.pires.obd.commands.fuel.FuelTrimCommand;
import com.github.pires.obd.commands.fuel.WidebandAirFuelRatioCommand;
import com.github.pires.obd.commands.pressure.BarometricPressureCommand;
import com.github.pires.obd.commands.pressure.FuelPressureCommand;
import com.github.pires.obd.commands.pressure.FuelRailPressureCommand;
import com.github.pires.obd.commands.pressure.IntakeManifoldPressureCommand;
import com.github.pires.obd.commands.temperature.AirIntakeTemperatureCommand;
import com.github.pires.obd.commands.temperature.AmbientAirTemperatureCommand;
import com.github.pires.obd.commands.temperature.EngineCoolantTemperatureCommand;
import com.github.pires.obd.enums.FuelTrim;

import static com.github.pires.obd.enums.FuelTrim.SHORT_TERM_BANK_1;

/**
 * TODO put description
 */
public final class ObdConfig {

  // Edit (mrangelo) - this returns a map now instead of a list, here is where we keep default polling intervals in seconds
  // Entries are a hashmap of <ObdCommand, <reporting_period, polling_period>>
  public static Map<ObdCommand, Map.Entry<Integer,Integer>> getCommands() {
    ArrayList<ObdCommand> cmds = new ArrayList<ObdCommand>();
    Map<ObdCommand, Map.Entry<Integer, Integer>> map = new HashMap<ObdCommand, Map.Entry<Integer, Integer>>();

    // Control
    map.put(new SpeedCommand(),new AbstractMap.SimpleEntry(30,30));
    map.put(new DtcNumberCommand(),new AbstractMap.SimpleEntry(0,0));
    map.put(new TimingAdvanceCommand(),new AbstractMap.SimpleEntry(0,0));
    map.put(new TroubleCodesCommand(),new AbstractMap.SimpleEntry(0,0));
    map.put(new DistanceMILOnCommand(),new AbstractMap.SimpleEntry(60,60));
    map.put(new DistanceSinceCCCommand(),new AbstractMap.SimpleEntry(60,60));
    map.put(new EquivalentRatioCommand(),new AbstractMap.SimpleEntry(60,60));
    map.put(new ModuleVoltageCommand(),new AbstractMap.SimpleEntry(300,300));
    map.put(new PendingTroubleCodesCommand(),new AbstractMap.SimpleEntry(0,0));
    map.put(new PermanentTroubleCodesCommand(),new AbstractMap.SimpleEntry(0,0));
    map.put(new VinCommand(),new AbstractMap.SimpleEntry(0,0));

    // Engine
    map.put(new AbsoluteLoadCommand(),new AbstractMap.SimpleEntry(30,30));
    map.put(new LoadCommand(),new AbstractMap.SimpleEntry(30,30));
    map.put(new MassAirFlowCommand(),new AbstractMap.SimpleEntry(30,30));
    map.put(new OilTempCommand(),new AbstractMap.SimpleEntry(30,30));
    map.put(new RPMCommand(),new AbstractMap.SimpleEntry(30,30));
    map.put(new RuntimeCommand(),new AbstractMap.SimpleEntry(30,30));
    map.put(new RPMCommand(),new AbstractMap.SimpleEntry(30,30));
    map.put(new ThrottlePositionCommand(),new AbstractMap.SimpleEntry(30,30));


    // Fuel
    map.put(new AirFuelRatioCommand(),new AbstractMap.SimpleEntry(60,60));
    map.put(new FindFuelTypeCommand(),new AbstractMap.SimpleEntry(60,60));
    map.put(new FuelLevelCommand(),new AbstractMap.SimpleEntry(60,60));
    map.put(new FuelTrimCommand(FuelTrim.SHORT_TERM_BANK_1),new AbstractMap.SimpleEntry(60,60));
    map.put(new FuelTrimCommand(FuelTrim.SHORT_TERM_BANK_2),new AbstractMap.SimpleEntry(60,60));
    map.put(new FuelTrimCommand(FuelTrim.LONG_TERM_BANK_1),new AbstractMap.SimpleEntry(60,60));
    map.put(new FuelTrimCommand(FuelTrim.LONG_TERM_BANK_2),new AbstractMap.SimpleEntry(60,60));
    map.put(new WidebandAirFuelRatioCommand(),new AbstractMap.SimpleEntry(60,60));
    map.put(new ConsumptionRateCommand(),new AbstractMap.SimpleEntry(60,60));


    // Pressure
    map.put(new BarometricPressureCommand(),new AbstractMap.SimpleEntry(30,30));
    map.put(new FuelPressureCommand(),new AbstractMap.SimpleEntry(30,30));
    map.put(new FuelRailPressureCommand(),new AbstractMap.SimpleEntry(30,30));
    map.put(new IntakeManifoldPressureCommand(),new AbstractMap.SimpleEntry(30,30));


    // Temperature
    map.put(new AirIntakeTemperatureCommand(),new AbstractMap.SimpleEntry(30,30));
    map.put(new AmbientAirTemperatureCommand(),new AbstractMap.SimpleEntry(30,30));
    map.put(new EngineCoolantTemperatureCommand(),new AbstractMap.SimpleEntry(30,30));

    return map;
  }

}
