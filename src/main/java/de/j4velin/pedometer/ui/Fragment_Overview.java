/*
 * Copyright 2014 Thomas Hoffmann
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
package de.j4velin.pedometer.ui;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.eazegraph.lib.charts.BarChart;
import org.eazegraph.lib.charts.PieChart;
import org.eazegraph.lib.models.BarModel;
import org.eazegraph.lib.models.PieModel;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.DataPointInterface;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.OnDataPointTapListener;
import com.jjoe64.graphview.series.Series;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

import de.j4velin.pedometer.BuildConfig;
import de.j4velin.pedometer.Database;
import de.j4velin.pedometer.R;
import de.j4velin.pedometer.SensorListener;
import de.j4velin.pedometer.util.API26Wrapper;
import de.j4velin.pedometer.util.Logger;
import de.j4velin.pedometer.util.Util;

public class Fragment_Overview extends Fragment implements SensorEventListener, OnClickListener {

    private TextView stepsView, totalView, averageView;
    private PieModel sliceGoal, sliceCurrent;
    private PieChart pg;

    private int todayOffset, total_start, goal, since_boot, total_days, resetOffset;
    public final static NumberFormat formatter = NumberFormat.getInstance(Locale.getDefault());
    private boolean showSteps = true;
    private boolean darkThemeEnabled;
    private GraphView linegraph;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);


        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

        Database db = Database.getInstance(getActivity());

        String savedString = prefs.getString("yvalues", "");
        if (savedString.length() != 0) {
            StringTokenizer stTokenizer = new StringTokenizer(savedString, ",");
            for (int i = 0; i < db.getYValuesLength(); i++) {
                db.setYValue(i, Integer.parseInt(stTokenizer.nextToken()));
            }
        }

        if (prefs.getBoolean("darkmode", false)) {
            darkThemeEnabled = true;
            getActivity().setTheme(R.style.DarkTheme);
        } else {
            darkThemeEnabled = false;
            getActivity().setTheme(R.style.LightTheme);
        }

        if (Build.VERSION.SDK_INT >= 26) {
            API26Wrapper.startForegroundService(getActivity(),
                    new Intent(getActivity(), SensorListener.class));
        } else {
            getActivity().startService(new Intent(getActivity(), SensorListener.class));
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.fragment_overview, null);
        stepsView = (TextView) v.findViewById(R.id.steps);
        totalView = (TextView) v.findViewById(R.id.total);
        averageView = (TextView) v.findViewById(R.id.average);

        pg = (PieChart) v.findViewById(R.id.graph);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

        ToggleButton toggle = (ToggleButton) v.findViewById(R.id.toggleButton);
        toggle.setChecked(false);

        Button b = (Button) v.findViewById(R.id.reset);
        b.setOnClickListener(this);

        if (prefs.getBoolean("darkmode", false)) {
            getActivity().setTheme(R.style.DarkTheme);
            if (!darkThemeEnabled) {
                getActivity().recreate();
            }
            sliceCurrent = new PieModel("", 0, Color.parseColor("#15CBEB"));
            sliceGoal = new PieModel("", 0, Color.parseColor("#707070"));
        } else {
            getActivity().setTheme(R.style.LightTheme);
            if (darkThemeEnabled) {
                getActivity().recreate();
            }
            sliceCurrent = new PieModel("", 0, Color.parseColor("#99CC00"));
            sliceGoal = new PieModel("", Fragment_Settings.DEFAULT_GOAL, Color.parseColor("#CC0000"));
        }

        pg.addPieSlice(sliceCurrent);
        pg.addPieSlice(sliceGoal);

        pg.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View view) {
                showSteps = !showSteps;
                stepsDistanceChanged();
            }
        });

        pg.setDrawValueInPie(false);
        pg.setUsePieRotation(true);
        pg.startAnimation();
        return v;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.reset:
                resetOffset = todayOffset;
                updatePie();
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // get your ToggleButton
        final ToggleButton toggle = (ToggleButton) getView().findViewById(R.id.toggleButton);

        // attach an OnClickListener
        toggle.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (toggle.isChecked()) {
                    getView().findViewById(R.id.bargraph).setVisibility(View.GONE);
                } else {
                    getView().findViewById(R.id.bargraph).setVisibility(View.VISIBLE);
                }
            }
        });

        Database db = Database.getInstance(getActivity());

        linegraph = (GraphView) getView().findViewById(R.id.linegraph);
        LineGraphSeries<DataPoint> series = new LineGraphSeries<DataPoint>(new DataPoint[] {
                new DataPoint(0, db.getYValue(0)),
                new DataPoint(1, db.getYValue(1)),
                new DataPoint(2, db.getYValue(2)),
                new DataPoint(3, db.getYValue(3)),
                new DataPoint(4, db.getYValue(4)),
                new DataPoint(5, db.getYValue(5)),
                new DataPoint(6, db.getYValue(6)),
                new DataPoint(7, db.getYValue(7)),
                new DataPoint(8, db.getYValue(8)),
                new DataPoint(9, db.getYValue(9)),
                new DataPoint(10, db.getYValue(10)),
                new DataPoint(11, db.getYValue(11)),
                new DataPoint(12, db.getYValue(12)),
                new DataPoint(13, db.getYValue(13)),
                new DataPoint(14, db.getYValue(14)),
                new DataPoint(15, db.getYValue(15)),
                new DataPoint(16, db.getYValue(16)),
                new DataPoint(17, db.getYValue(17)),
                new DataPoint(18, db.getYValue(18)),
                new DataPoint(19, db.getYValue(19)),
                new DataPoint(20, db.getYValue(20)),
                new DataPoint(21, db.getYValue(21)),
                new DataPoint(22, db.getYValue(22)),
                new DataPoint(23, db.getYValue(23))
        });
        linegraph.addSeries(series);
        series.setDrawDataPoints(true);

        // register tap on series callback
        series.setOnDataPointTapListener(new OnDataPointTapListener() {
            @Override
            public void onTap(Series series, DataPointInterface dataPoint) {
                Toast.makeText(linegraph.getContext(), "Amount of steps taken:" + dataPoint.getY() + "", Toast.LENGTH_SHORT).show();
            }
        });

        if (darkThemeEnabled) {
            linegraph.getGridLabelRenderer().setGridColor(Color.WHITE);
            linegraph.getGridLabelRenderer().setHorizontalLabelsColor(Color.WHITE);
            linegraph.getGridLabelRenderer().setVerticalLabelsColor(Color.WHITE);
            linegraph.getGridLabelRenderer().setHorizontalAxisTitleColor(Color.WHITE);
            linegraph.getGridLabelRenderer().setVerticalAxisTitleColor(Color.WHITE);
            linegraph.getGridLabelRenderer().reloadStyles();
        } else {
            linegraph.getGridLabelRenderer().setGridColor(Color.BLACK);
            linegraph.getGridLabelRenderer().setHorizontalLabelsColor(Color.BLACK);
            linegraph.getGridLabelRenderer().setVerticalLabelsColor(Color.BLACK);
            linegraph.getGridLabelRenderer().setHorizontalAxisTitleColor(Color.BLACK);
            linegraph.getGridLabelRenderer().setVerticalAxisTitleColor(Color.BLACK);
            linegraph.getGridLabelRenderer().reloadStyles();
        }

        linegraph.setTitle("Hourly Chart");
        linegraph.getGridLabelRenderer().setVerticalAxisTitle("Steps");
        linegraph.getGridLabelRenderer().setHorizontalAxisTitle("Hour (24hr format)");

        // set manual X bounds
        linegraph.getViewport().setXAxisBoundsManual(true);

        Calendar rightNow = Calendar.getInstance();
        int currentHourIn24Format = rightNow.get(Calendar.HOUR_OF_DAY);

        int minview = currentHourIn24Format - 6 > 0 ? currentHourIn24Format : 0;
        int maxView = minview == 0 ? maxView = 12 : currentHourIn24Format + 6;
        maxView = maxView > 23 ? 23 : maxView;
        minview = maxView == 23 ? minview = 11 : minview;

        linegraph.getViewport().setMinX(minview);
        linegraph.getViewport().setMaxX(maxView);
        linegraph.getViewport().getMinX(false);

        // enable scrolling
        linegraph.getViewport().setScrollable(true);

        getActivity().getActionBar().setDisplayHomeAsUpEnabled(false);

        if (BuildConfig.DEBUG) db.logState();
        // read todays offset
        todayOffset = db.getSteps(Util.getToday());

        SharedPreferences prefs =
                getActivity().getSharedPreferences("pedometer", Context.MODE_PRIVATE);

        goal = prefs.getInt("goal", Fragment_Settings.DEFAULT_GOAL);
        since_boot = db.getCurrentSteps();
        int pauseDifference = since_boot - prefs.getInt("pauseCount", since_boot);

        // register a sensorlistener to live update the UI if a step is taken
        SensorManager sm = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        Sensor sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (sensor == null) {
            new AlertDialog.Builder(getActivity()).setTitle(R.string.no_sensor)
                    .setMessage(R.string.no_sensor_explain)
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(final DialogInterface dialogInterface) {
                            getActivity().finish();
                        }
                    }).setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            }).create().show();
        } else {
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI, 0);
        }

        since_boot -= pauseDifference;

        total_start = db.getTotalWithoutToday();
        total_days = db.getDays();

        db.close();

        stepsDistanceChanged();
    }

    /**
     * Call this method if the Fragment should update the "steps"/"km" text in
     * the pie graph as well as the pie and the bars graphs.
     */
    private void stepsDistanceChanged() {
        if (showSteps) {
            ((TextView) getView().findViewById(R.id.unit)).setText(getString(R.string.steps));
        } else {
            String unit = getActivity().getSharedPreferences("pedometer", Context.MODE_PRIVATE)
                    .getString("stepsize_unit", Fragment_Settings.DEFAULT_STEP_UNIT);
            if (unit.equals("cm")) {
                unit = "km";
            } else {
                unit = "mi";
            }
            ((TextView) getView().findViewById(R.id.unit)).setText(unit);
        }

        updatePie();
        updateBars();
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            SensorManager sm =
                    (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
            sm.unregisterListener(this);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Logger.log(e);
        }
        Database db = Database.getInstance(getActivity());
        db.saveCurrentSteps(since_boot);
        db.close();
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.main, menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_split_count:
                Dialog_Split.getDialog(getActivity(),
                        total_start + Math.max(todayOffset + since_boot, 0)).show();
                return true;
            default:
                return ((Activity_Main) getActivity()).optionsItemSelected(item);
        }
    }

    @Override
    public void onAccuracyChanged(final Sensor sensor, int accuracy) {
        // won't happen
    }

    @Override
    public void onSensorChanged(final SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        double total = (x * x + y * y + z * z)/(SensorManager.GRAVITY_EARTH * SensorManager.GRAVITY_EARTH);
        Database db = Database.getInstance(getActivity());

        if (BuildConfig.DEBUG) Logger.log(
                "UI - sensorChanged | todayOffset: " + todayOffset + " since boot: " +
                        total);
        if (total > Integer.MAX_VALUE || total == 0) {
            return;
        }
        if (todayOffset == Integer.MIN_VALUE) {
            // no values for today
            // we dont know when the reboot was, so set todays steps to 0 by
            // initializing them with -STEPS_SINCE_BOOT
            todayOffset = -(int) total;
            db.insertNewDay(Util.getToday(), (int) total);
        }

        if ((int) total >  1) {
            Calendar rightNow = Calendar.getInstance();
            int currentHourIn24Format = rightNow.get(Calendar.HOUR_OF_DAY);

            double minXval = linegraph.getViewport().getMinX(false);
            double maxXval = linegraph.getViewport().getMaxX(false);

            if (currentHourIn24Format != db.getCurrentHour()) {
                if (currentHourIn24Format < db.getCurrentHour()) {
                    db.clearYValues();
                }
                db.setStepsInHour(0);
                db.setCurrentHour(currentHourIn24Format);
            }

            since_boot++;
            db.setStepsInHour(db.getStepsInHour() + 1);
            db.setYValue(currentHourIn24Format, db.getStepsInHour());

            final GraphView linegraph = (GraphView) getView().findViewById(R.id.linegraph);
            linegraph.removeAllSeries();

            LineGraphSeries<DataPoint> series = new LineGraphSeries<DataPoint>(new DataPoint[] {
                    new DataPoint(0, db.getYValue(0)),
                    new DataPoint(1, db.getYValue(1)),
                    new DataPoint(2, db.getYValue(2)),
                    new DataPoint(3, db.getYValue(3)),
                    new DataPoint(4, db.getYValue(4)),
                    new DataPoint(5, db.getYValue(5)),
                    new DataPoint(6, db.getYValue(6)),
                    new DataPoint(7, db.getYValue(7)),
                    new DataPoint(8, db.getYValue(8)),
                    new DataPoint(9, db.getYValue(9)),
                    new DataPoint(10, db.getYValue(10)),
                    new DataPoint(11, db.getYValue(11)),
                    new DataPoint(12, db.getYValue(12)),
                    new DataPoint(13, db.getYValue(13)),
                    new DataPoint(14, db.getYValue(14)),
                    new DataPoint(15, db.getYValue(15)),
                    new DataPoint(16, db.getYValue(16)),
                    new DataPoint(17, db.getYValue(17)),
                    new DataPoint(18, db.getYValue(18)),
                    new DataPoint(19, db.getYValue(19)),
                    new DataPoint(20, db.getYValue(20)),
                    new DataPoint(21, db.getYValue(21)),
                    new DataPoint(22, db.getYValue(22)),
                    new DataPoint(23, db.getYValue(23))
            });
            linegraph.addSeries(series);
            series.setDrawDataPoints(true);

            // register tap on series callback
            series.setOnDataPointTapListener(new OnDataPointTapListener() {
                @Override
                public void onTap(Series series, DataPointInterface dataPoint) {
                    Toast.makeText(linegraph.getContext(), "Amount of steps taken:" + dataPoint.getY() + "", Toast.LENGTH_SHORT).show();
                }
            });

            if (darkThemeEnabled) {
                linegraph.getGridLabelRenderer().setGridColor(Color.WHITE);
                linegraph.getGridLabelRenderer().setHorizontalLabelsColor(Color.WHITE);
                linegraph.getGridLabelRenderer().setVerticalLabelsColor(Color.WHITE);
                linegraph.getGridLabelRenderer().setHorizontalAxisTitleColor(Color.WHITE);
                linegraph.getGridLabelRenderer().setVerticalAxisTitleColor(Color.WHITE);
                linegraph.getGridLabelRenderer().reloadStyles();
            } else {
                linegraph.getGridLabelRenderer().setGridColor(Color.BLACK);
                linegraph.getGridLabelRenderer().setHorizontalLabelsColor(Color.BLACK);
                linegraph.getGridLabelRenderer().setVerticalLabelsColor(Color.BLACK);
                linegraph.getGridLabelRenderer().setHorizontalAxisTitleColor(Color.BLACK);
                linegraph.getGridLabelRenderer().setVerticalAxisTitleColor(Color.BLACK);
                linegraph.getGridLabelRenderer().reloadStyles();
            }

            linegraph.setTitle("Hourly Chart");
            linegraph.getGridLabelRenderer().setVerticalAxisTitle("Steps");
            linegraph.getGridLabelRenderer().setHorizontalAxisTitle("Hour (24hr format)");

            // set manual X bounds
            linegraph.getViewport().setXAxisBoundsManual(true);
            linegraph.getViewport().setMinX(minXval);
            linegraph.getViewport().setMaxX(maxXval);

            // enable scrolling
            linegraph.getViewport().setScrollable(true);


            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            StringBuilder string = new StringBuilder();
            for (int i = 0; i < db.getYValuesLength(); i++) {
                string.append(db.getYValue(i)).append(",");
            }

            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("yvalues",string.toString());
            editor.apply();


        }

        db.close();
        updatePie();
    }

    /**
     * Updates the pie graph to show todays steps/distance as well as the
     * yesterday and total values. Should be called when switching from step
     * count to distance.
     */
    private void updatePie() {
        if (BuildConfig.DEBUG) Logger.log("UI - update steps: " + since_boot);
        // todayOffset might still be Integer.MIN_VALUE on first start
        int steps_today = Math.max(todayOffset + since_boot - resetOffset, 0);
        sliceCurrent.setValue(steps_today);
        if (goal - steps_today > 0) {
            // goal not reached yet
            if (pg.getData().size() == 1) {
                // can happen if the goal value was changed: old goal value was
                // reached but now there are some steps missing for the new goal
                pg.addPieSlice(sliceGoal);
            }
            sliceGoal.setValue(goal - steps_today);
        } else {
            // goal reached
            pg.clearChart();
            pg.addPieSlice(sliceCurrent);
        }
        pg.update();
        if (showSteps) {
            stepsView.setText(formatter.format(steps_today));
            totalView.setText(formatter.format(total_start + steps_today));
            averageView.setText(formatter.format((total_start + steps_today) / total_days));
        } else {
            // update only every 10 steps when displaying distance
            SharedPreferences prefs =
                    getActivity().getSharedPreferences("pedometer", Context.MODE_PRIVATE);
            float stepsize = prefs.getFloat("stepsize_value", Fragment_Settings.DEFAULT_STEP_SIZE);
            float distance_today = steps_today * stepsize;
            float distance_total = (total_start + steps_today) * stepsize;
            if (prefs.getString("stepsize_unit", Fragment_Settings.DEFAULT_STEP_UNIT)
                    .equals("cm")) {
                distance_today /= 100000;
                distance_total /= 100000;
            } else {
                distance_today /= 5280;
                distance_total /= 5280;
            }
            stepsView.setText(formatter.format(distance_today));
            totalView.setText(formatter.format(distance_total));
            averageView.setText(formatter.format(distance_total / total_days));
        }
    }

    /**
     * Updates the bar graph to show the steps/distance of the last week. Should
     * be called when switching from step count to distance.
     */
    private void updateBars() {
        SimpleDateFormat df = new SimpleDateFormat("E", Locale.getDefault());
        BarChart barChart = (BarChart) getView().findViewById(R.id.bargraph);
        if (barChart.getData().size() > 0) barChart.clearChart();
        int steps;
        float distance, stepsize = Fragment_Settings.DEFAULT_STEP_SIZE;
        boolean stepsize_cm = true;
        if (!showSteps) {
            // load some more settings if distance is needed
            SharedPreferences prefs =
                    getActivity().getSharedPreferences("pedometer", Context.MODE_PRIVATE);
            stepsize = prefs.getFloat("stepsize_value", Fragment_Settings.DEFAULT_STEP_SIZE);
            stepsize_cm = prefs.getString("stepsize_unit", Fragment_Settings.DEFAULT_STEP_UNIT)
                    .equals("cm");
        }
        barChart.setShowDecimal(!showSteps); // show decimal in distance view only
        BarModel bm;
        Database db = Database.getInstance(getActivity());
        List<Pair<Long, Integer>> last = db.getLastEntries(8);
        db.close();
        for (int i = last.size() - 1; i > 0; i--) {
            Pair<Long, Integer> current = last.get(i);
            steps = 10000;
            if (steps > 0) {
                bm = new BarModel(df.format(new Date(current.first)), 0,
                        steps > goal ? Color.parseColor("#99CC00") : Color.parseColor("#0099cc"));
                if (showSteps) {
                    bm.setValue(steps);
                } else {
                    distance = steps * stepsize;
                    if (stepsize_cm) {
                        distance /= 100000;
                    } else {
                        distance /= 5280;
                    }
                    distance = Math.round(distance * 1000) / 1000f; // 3 decimals
                    bm.setValue(distance);
                }
                barChart.addBar(bm);
            }
        }
        if (barChart.getData().size() > 0) {
            barChart.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(final View v) {
                    Dialog_Statistics.getDialog(getActivity(), since_boot).show();
                }
            });
            barChart.startAnimation();
        } else {
            barChart.setVisibility(View.GONE);
        }
    }

}
