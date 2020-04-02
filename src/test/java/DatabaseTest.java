import android.database.Cursor;
import android.test.InstrumentationTestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Calendar;
import java.util.Date;

import de.j4velin.pedometer.BuildConfig;
import de.j4velin.pedometer.Database;
import de.j4velin.pedometer.util.Util;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, manifest=Config.NONE, sdk = 21)
public class DatabaseTest extends InstrumentationTestCase {
    private Database db;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        db = Database.getInstance(RuntimeEnvironment.application);
    }

    @After
    public void tearDown() throws Exception {
        db.close();
        super.tearDown();
    }

    @Test
    public void testPreConditions() {
        assertNotNull(db);
    }

    // White box testing
    // Data flow testing with structure basis testing where appropriate

    // onCreate and onUpgrade are not tested as they are used by the parent class and not the user
    // logger was not tested as it just uses the logging library to display db to programmer

    @Test
    public void testQuery() {
        Cursor c =
            db.query(new String[]{"date", "steps"}, "date > 0", null, null,
                    null, "date", null);

        assertNotNull(c);
    }

    @Test
    public void testInsertandGetNewDayandGetSteps() {
        double total = 1000;
        db.insertNewDay(0, (int) total);
        assertEquals(db.getSteps(0, 0), -1000);
    }

    @Test
    public void testInsertDataFromBackup() {
        double total = 1000;
        db.insertDayFromBackup(0, (int) total);
        assertEquals(db.getSteps(0, 0), 1000);
    }

    @Test
    public void testAddToLastEntry() {
        db.addToLastEntry(500);
        assertEquals(db.getSteps(0, 0), 0);
        double total = 1000;
        db.insertDayFromBackup(0, (int) total);
        db.addToLastEntry(500);
        assertEquals(db.getSteps(0, 0), 1500);
    }

    @Test
    public void testgetRecord() {
        assertEquals(db.getRecord(), 0);
        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        //day before
        db.insertDayFromBackup(cal.getTimeInMillis(), 1000);
        //today
        db.insertDayFromBackup(Util.getToday(), (int) 10000);
        assertEquals(db.getRecord(), 10000);
    }

    @Test
    public void testgetRecordData() {
        // programmer did not account for no data
        // assertEquals(db.getRecordData(), true);
        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        //day before
        db.insertDayFromBackup(cal.getTimeInMillis(), 1000);
        //today
        db.insertDayFromBackup(Util.getToday(), (int) 10000);
        Date time = db.getRecordData().first;
        int steps = db.getRecordData().second;
        assertEquals(time, new Date(Util.getToday()));
        assertEquals(steps, 10000);
    }

    @Test
    public void testLastEntries() {
        assertEquals(db.getLastEntries(1).isEmpty(), true);
        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        //day before
        db.insertDayFromBackup(cal.getTimeInMillis(), 2000);
        //today
        db.insertDayFromBackup(Util.getToday(), (int) 1000);
        long time = db.getLastEntries(1).get(0).first;
        int steps = db.getLastEntries(1).get(0).second;
        assertEquals(time, Util.getToday());
        assertEquals(steps, 1000);
        time = db.getLastEntries(2).get(1).first;
        steps = db.getLastEntries(2).get(1).second;
        assertEquals(time, cal.getTimeInMillis());
        assertEquals(steps, 2000);
    }

    @Test
    public void testGetDaysandDaysWithoutToday() {
        assertEquals(db.getDays(), 1);
        double total = 1000;
        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        //day before
        db.insertNewDay(cal.getTimeInMillis(), (int) total);
        //today
        db.insertNewDay(Util.getToday(), (int) total);
        assertEquals(db.getDays(), 1);
        cal.add(Calendar.DATE, -2);
        // two days before
        db.insertDayFromBackup(cal.getTimeInMillis(), (int) total);
        assertEquals(db.getDays(), 2);
    }

    @Test
    public void testremoveNegativeEntries() {
        db.removeNegativeEntries();
        assertEquals(db.getSteps(0, 0), 0);
        db.insertDayFromBackup(0, -1000);
        db.removeNegativeEntries();
        assertEquals(db.getSteps(0, 0), 0);
    }

    @Test
    public void testremoveInvalidEntries() {
        db.removeInvalidEntries();
        assertEquals(db.getSteps(0, 0), 0);
        db.insertDayFromBackup(0, 2000000000);
        db.removeInvalidEntries();
        assertEquals(db.getSteps(0, 0), 0);
    }

    @Test
    public void testSaveandGetCurrentSteps() {
        assertEquals(db.getCurrentSteps(), 0);
        db.saveCurrentSteps(1000);
        db.addToLastEntry(10000);
        assertEquals(db.getCurrentSteps(), 11000);
    }

    // New functions added to database

    @Test
    public void testgetanndSetCurrentHour() {
        db.setStepsInHour(100);
        assertEquals(db.getStepsInHour(), 100);
    }

    @Test
    public void testgetanndSetStepsHour() {
        db.setCurrentHour(12);
        assertEquals(db.getCurrentHour(), 12);
    }

    @Test
    public void testsetYValue() {
        db.setYValue(1, 100);
        int value = db.getYValue(1);
        assertEquals(value, 100);
    }

    @Test
    public void testgetYValue() {
        int value = db.getYValue(23);
        assertEquals(value, 0);
    }

    @Test
    public void testgetYValuesLength() {
        assertEquals(db.getYValuesLength(), 24);
    }

    @Test
    public void testclearYValues() {
        db.setYValue(1, 100);
        db.setYValue(3, 100);
        db.clearYValues();
        assertEquals(db.getYValue(1), 0);
        assertEquals(db.getYValue(3), 0);
    }

}
