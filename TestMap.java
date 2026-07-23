import java.lang.reflect.Constructor;
import mindustry.maps.Map;

public class TestMap {
    public static void main(String[] args) {
        System.out.println("Constructors for Map:");
        for (Constructor<?> c : Map.class.getConstructors()) {
            System.out.println(c.toString());
        }
    }
}
