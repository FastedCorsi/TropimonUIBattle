package fr.tropimon.battleui;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
final class ValueCacheTest {
    record Key(String pokemon, float hp, int pp, List<String> effects, int width, String language, long resources) { }
    @Test void tooltipCachingTracksValuesInsteadOfTicks() {
        var cache=new LastValueCache<Key,Integer>(); var builds=new AtomicInteger();
        var key=new Key("zoroark-hisui",0.04f,3,List.of("rain"),200,"fr",0);
        for(int frame=0;frame<2000;frame++) assertEquals(1,cache.get(key,builds::incrementAndGet));
        assertEquals(1,builds.get());
        for(var next:List.of(
                new Key("zoroark-hisui",0.03f,3,List.of("rain"),200,"fr",0),
                new Key("zoroark",0.03f,3,List.of("rain"),200,"fr",0),
                new Key("zoroark",0.03f,2,List.of("rain"),200,"fr",0),
                new Key("zoroark",0.03f,2,List.of("sun"),200,"fr",0),
                new Key("zoroark",0.03f,2,List.of("sun"),100,"fr",0),
                new Key("zoroark",0.03f,2,List.of("sun"),100,"en",0),
                new Key("zoroark",0.03f,2,List.of("sun"),100,"en",1))) cache.get(next,builds::incrementAndGet);
        assertEquals(8,builds.get());
    }
}
