package fr.tropimon.battleui;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
final class TeamSnapshotTest {
    @BeforeAll static void bootstrap() { DamageCacheParityTest.bootstrap(); }
    @Test void identicalTeamSnapshotsKeepIdentityButHealthMovesStatusAndItemsDoNotFreeze() {
        UUID id=UUID.randomUUID();
        TeamMemberView a=TeamMemberView.snapshot(null,id,"Zoroark-Hisui",100,50,"",false,true,
                ItemStack.EMPTY,null,List.of(),null,List.of(),List.of(),true);
        assertSame(a,TeamMemberView.snapshot(a,id,"Zoroark-Hisui",100,50,"",false,true,
                ItemStack.EMPTY,null,List.of(),null,List.of(),List.of(),true));
        assertSame(a,a.withActive(true));
        assertSame(a,a.withHealth(50));
        assertNotSame(a,a.withHealth(0.04f));
        assertNotSame(a,TeamMemberView.snapshot(a,id,"Zoroark",100,50,"brn",false,true,
                ItemStack.EMPTY,null,List.of(),null,List.of(),List.of(),true));
        ItemStack source=new ItemStack(Items.STONE);
        var b=a.withItem(source);
        source.setCount(2);
        assertEquals(1,b.heldItem().getCount());
        assertNotSame(b,b.withItem(source));
        MoveView move=new MoveView("surf",null,null,"water",20,24);
        var c=TeamMemberView.snapshot(a,id,"Zoroark-Hisui",100,50,"",false,true,
                ItemStack.EMPTY,null,List.of(),null,List.of(move),List.of(),true);
        assertNotSame(c,TeamMemberView.snapshot(c,id,"Zoroark-Hisui",100,50,"",false,true,
                ItemStack.EMPTY,null,List.of(),null,List.of(move.spendPp(1)),List.of(),true));
    }
}
