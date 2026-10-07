package dao.model;
import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;
public class TimestampClockSkewTest {
    @Test public void freshServerTimestampToleratesSmallDeviceClockSkew() {
        Locale before=Locale.getDefault();
        try {
            Locale.setDefault(Locale.ENGLISH);
            var formatter=new TimestampFormatterTimeSinceEnglish();
            assertEquals("right now",formatter.format(System.currentTimeMillis()+5000));
            assertEquals("in the future",formatter.format(System.currentTimeMillis()+120000));
        } finally {Locale.setDefault(before);}
    }
}
