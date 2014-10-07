/*
 *  Copyright (C) 2013 - 2014 Alexander "Evisceration" Martinz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.namelessrom.devicecontrol.hardware;

import android.text.TextUtils;

import com.stericson.roottools.RootTools;
import com.stericson.roottools.execution.CommandCapture;
import com.stericson.roottools.execution.Shell;

import org.namelessrom.devicecontrol.Application;
import org.namelessrom.devicecontrol.Logger;
import org.namelessrom.devicecontrol.R;
import org.namelessrom.devicecontrol.database.DataItem;
import org.namelessrom.devicecontrol.database.DatabaseHandler;
import org.namelessrom.devicecontrol.hardware.monitors.CpuStateMonitor;
import org.namelessrom.devicecontrol.objects.CpuCore;
import org.namelessrom.devicecontrol.utils.Utils;
import org.namelessrom.devicecontrol.utils.constants.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generic CPU Tasks.
 */
public class CpuUtils implements Constants {

    public static class Frequency {
        public final String[] available;
        public final String   maximum;
        public final String   minimum;

        public Frequency(final String[] avail, final String max, final String min) {
            available = avail;
            maximum = max;
            minimum = min;
        }
    }

    public static class Cores {
        public final List<CpuCore> list;

        public Cores(final List<CpuCore> coreList) { list = coreList; }
    }

    public static class State {
        public final List<CpuStateMonitor.CpuState> states;
        public final long                           totalTime;

        public State(final List<CpuStateMonitor.CpuState> stateList, final long totalStateTime) {
            states = stateList;
            totalTime = totalStateTime;
        }
    }

    public interface FrequencyListener {
        public void onFrequency(final Frequency cpuFreq);
    }

    public interface CoreListener {
        public void onCores(final Cores cores);
    }

    public interface StateListener {
        public void onStates(final State states);
    }

    private static CpuUtils sInstance;

    private CpuUtils() { }

    public static CpuUtils get() {
        if (sInstance == null) {
            sInstance = new CpuUtils();
        }
        return sInstance;
    }

    public String getCpuFrequencyPath(final int cpu) {
        switch (cpu) {
            default:
            case 0:
                return CPU0_FREQ_CURRENT_PATH;
            case 1:
                return CPU1_FREQ_CURRENT_PATH;
            case 2:
                return CPU2_FREQ_CURRENT_PATH;
            case 3:
                return CPU3_FREQ_CURRENT_PATH;
        }
    }

    public String getMaxCpuFrequencyPath(final int cpu) {
        switch (cpu) {
            default:
            case 0:
                return FREQ0_MAX_PATH;
            case 1:
                return FREQ1_MAX_PATH;
            case 2:
                return FREQ2_MAX_PATH;
            case 3:
                return FREQ3_MAX_PATH;
        }
    }

    public String getMinCpuFrequencyPath(final int cpu) {
        switch (cpu) {
            default:
            case 0:
                return FREQ0_MIN_PATH;
            case 1:
                return FREQ1_MIN_PATH;
            case 2:
                return FREQ2_MIN_PATH;
            case 3:
                return FREQ3_MIN_PATH;
        }
    }

    public String getOnlinePath(final int cpu) {
        switch (cpu) {
            default:
            case 0:
                return "";
            case 1:
                return CORE1_ONLINE;
            case 2:
                return CORE2_ONLINE;
            case 3:
                return CORE3_ONLINE;
        }
    }

    public int getCpuTemperature() {
        String tmpString = Utils.readOneLine(CPU_TEMP_PATH);
        if (!TextUtils.isEmpty(tmpString) && !tmpString.trim().isEmpty()) {
            int temp;
            try { temp = Integer.parseInt(tmpString);} catch (Exception e) { return -1; }
            temp = (temp < 0 ? 0 : temp);
            temp = (temp > 100 ? 100 : temp);
            return temp;
        }
        return -1;
    }

    public String[] getAvailableFrequencies() {
        final String freqsRaw = Utils.readOneLine(FREQ_AVAILABLE_PATH);
        if (freqsRaw != null && !freqsRaw.isEmpty()) {
            return freqsRaw.split(" ");
        }
        return null;
    }

    /**
     * Get total number of cpus
     *
     * @return total number of cpus
     */
    public int getNumOfCpus() {
        int numOfCpu = 1;
        final String numOfCpus = Utils.readOneLine(PRESENT_CPUS);
        if (numOfCpus != null && !numOfCpus.isEmpty()) {
            final String[] cpuCount = numOfCpus.split("-");
            if (cpuCount.length > 1) {
                try {
                    numOfCpu = Integer.parseInt(cpuCount[1]) - Integer.parseInt(cpuCount[0]) + 1;
                    if (numOfCpu < 0) {
                        numOfCpu = 1;
                    }
                } catch (NumberFormatException ex) {
                    numOfCpu = 1;
                }
            }
        }
        return numOfCpu;
    }

    public String restore() {
        final StringBuilder sbCmd = new StringBuilder();

        final List<DataItem> items = DatabaseHandler.getInstance().getAllItems(
                DatabaseHandler.TABLE_BOOTUP, DatabaseHandler.CATEGORY_CPU);
        String tmpString;
        int tmpInt;
        for (final DataItem item : items) {
            tmpInt = -1;
            tmpString = item.getName();
            if (tmpString != null && !tmpString.contains("io")) {
                try {
                    tmpInt = Integer.parseInt(
                            String.valueOf(tmpString.charAt(tmpString.length() - 1)));
                } catch (Exception exc) {
                    tmpInt = -1;
                }
            }
            if (tmpInt != -1) {
                final String path = getOnlinePath(tmpInt);
                if (path != null && !path.isEmpty()) {
                    sbCmd.append(Utils.getWriteCommand(path, "0"));
                    sbCmd.append(Utils.getWriteCommand(path, "1"));
                }
            }
            sbCmd.append(Utils.getWriteCommand(item.getFileName(), item.getValue()));
        }

        return sbCmd.toString();
    }

    public void getCpuFreq(final FrequencyListener listener) {
        try {
            final Shell mShell = RootTools.getShell(true);
            if (mShell == null) { throw new Exception("Shell is null"); }

            final StringBuilder cmd = new StringBuilder();
            cmd.append("command=$(");
            cmd.append("cat ").append(FREQ_AVAILABLE_PATH).append(" 2> /dev/null;");
            cmd.append("echo -n \"[\";");
            cmd.append("cat ").append(FREQ0_MAX_PATH).append(" 2> /dev/null;");
            cmd.append("echo -n \"]\";");
            cmd.append("cat ").append(FREQ0_MIN_PATH).append(" 2> /dev/null;");
            cmd.append(");").append("echo $command | tr -d \"\\n\"");
            Logger.v(CpuUtils.class, cmd.toString());

            final StringBuilder outputCollector = new StringBuilder();
            final CommandCapture cmdCapture = new CommandCapture(0, false, cmd.toString()) {
                @Override
                public void commandOutput(int id, String line) {
                    outputCollector.append(line);
                    Logger.v(CpuUtils.class, line);
                }

                @Override
                public void commandCompleted(int id, int exitcode) {
                    final List<String> result =
                            Arrays.asList(outputCollector.toString().split(" "));
                    final List<String> tmpList = new ArrayList<String>();
                    String tmpMax = "", tmpMin = "";

                    if (result.size() <= 0) return;

                    for (final String s : result) {
                        if (s.isEmpty()) continue;
                        if (s.charAt(0) == '[') {
                            tmpMax = s.substring(1, s.length());
                        } else if (s.charAt(0) == ']') {
                            tmpMin = s.substring(1, s.length());
                        } else {
                            tmpList.add(s);
                        }
                    }

                    final String max = tmpMax;
                    final String min = tmpMin;
                    final String[] avail = tmpList.toArray(new String[tmpList.size()]);
                    Application.HANDLER.post(new Runnable() {
                        @Override public void run() {
                            listener.onFrequency(new Frequency(avail, max, min));
                        }
                    });

                }
            };

            if (mShell.isClosed()) { throw new Exception("Shell is closed"); }
            mShell.add(cmdCapture);
        } catch (Exception exc) {
            Logger.e(CpuUtils.class, "Error: " + exc.getMessage());
        }
    }

    public String enableMpDecision(final boolean start) {
        return (start ? "start mpdecision 2> /dev/null;\n" : "stop mpdecision 2> /dev/null;\n");
    }

    public String onlineCpu(final int cpu) {
        final StringBuilder sb = new StringBuilder();
        final String pathOnline = getOnlinePath(cpu);
        if (!pathOnline.isEmpty()) {
            sb.append(Utils.getWriteCommand(pathOnline, "0"));
            sb.append(Utils.getWriteCommand(pathOnline, "1"));
        }
        return sb.toString();
    }

    /**
     * Convert to MHz and append a tag
     *
     * @param mhzString The string to convert to MHz
     * @return tagged and converted String
     */
    public static String toMHz(final String mhzString) {
        int value = -1;
        if (!TextUtils.isEmpty(mhzString)) {
            try {
                value = Integer.parseInt(mhzString) / 1000;
            } catch (NumberFormatException exc) {
                Logger.e(CpuUtils.get(), "toMHz", exc);
                value = -1;
            }
        }

        if (value != -1) {
            return String.valueOf(value) + " MHz";
        } else {
            return Application.get().getString(R.string.core_offline);
        }
    }

    /**
     * Convert from MHz to its original frequency
     *
     * @param mhzString The MHz string to convert to a frequency
     * @return the original frequency
     */
    public static String fromMHz(final String mhzString) {
        if (!TextUtils.isEmpty(mhzString)) {
            try {
                return String.valueOf(Integer.parseInt(mhzString.replace(" MHz", "")) * 1000);
            } catch (Exception exc) {
                Logger.e(CpuUtils.get(), exc.getMessage());
            }
        }
        return "0";
    }

}