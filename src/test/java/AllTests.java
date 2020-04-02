import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({ DatabaseTest.class }) // add others here, separated by comma
public class AllTests {
    // we may want this for shared setUp, teardown
}