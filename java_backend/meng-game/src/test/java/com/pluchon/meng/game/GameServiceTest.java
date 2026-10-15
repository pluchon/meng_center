package com.pluchon.meng.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.web.context.RequestIdentity;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.game.catalog.GameCatalog;
import com.pluchon.meng.game.config.GameProperties;
import com.pluchon.meng.game.controller.dto.EquipmentDTO;
import com.pluchon.meng.game.controller.dto.PickupDTO;
import com.pluchon.meng.game.controller.dto.PurchaseDTO;
import com.pluchon.meng.game.controller.dto.SaveDTO;
import com.pluchon.meng.game.converter.GameConverter;
import com.pluchon.meng.game.entity.GamePlayerEntity;
import com.pluchon.meng.game.entity.enums.ItemKind;
import com.pluchon.meng.game.entity.enums.SpotKind;
import com.pluchon.meng.game.mapper.GameMapper;
import com.pluchon.meng.game.service.GameError;
import com.pluchon.meng.game.service.GameService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

class GameServiceTest {

    // 2026-10-15 16:30 UTC 在台北已是 10 月 16 日
    static final String TODAY = "2026-10-16";

    GameMapper mapper;

    GameService service;

    GamePlayerEntity player;

    @BeforeEach
    void setup() {
        mapper = mock(GameMapper.class);
        player = new GamePlayerEntity();
        player.setUserId(7L);
        player.setCoins(0L);
        player.setPosX(0.0);
        player.setPosY(1.2);
        player.setPosZ(-100.0);
        player.setYaw(0.0);
        when(mapper.selectPlayer(7)).thenReturn(player);
        var catalog =
                new GameCatalog(
                        new GameCatalog.World(
                                new GameCatalog.Position(0, 1.2, -100, 0),
                                new GameCatalog.Point(44, 42),
                                List.of(
                                        new GameCatalog.Spot("coin-01", SpotKind.COIN, 0, -86, 3),
                                        new GameCatalog.Spot("chest-a", SpotKind.CHEST, 10, 10, 0),
                                        new GameCatalog.Spot("star-a", SpotKind.STAR, 20, 20, 0),
                                        new GameCatalog.Spot("star-b", SpotKind.STAR, 30, 30, 0))),
                        List.of(
                                new GameCatalog.Item("hat-leaf", ItemKind.HAT, 30),
                                new GameCatalog.Item("outfit-mint", ItemKind.OUTFIT, 20)));
        service = new GameService();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "catalog", catalog);
        ReflectionTestUtils.setField(service, "converter", new GameConverter());
        ReflectionTestUtils.setField(
                service, "properties", new GameProperties("Asia/Taipei", 3, 20, 100, 110, -10, 60));
        ReflectionTestUtils.setField(
                service,
                "clock",
                Clock.fixed(Instant.parse("2026-10-15T16:30:00Z"), ZoneOffset.UTC));
        UserContext.bind(new RequestIdentity(7, "USER", "test"));
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void newPlayerIsCreatedAtSpawn() {
        var profile = service.profile();
        verify(mapper).insertPlayerIfAbsent(7, 0, 1.2, -100, 0);
        assertEquals(0, profile.coins());
        assertEquals(0, profile.stars());
        assertFalse(profile.cleared());
    }

    @Test
    void coinIsTakenOncePerBusinessDay() {
        when(mapper.insertPickupIfAbsent(7, "coin-01", TODAY, 3)).thenReturn(1, 0);
        when(mapper.selectCoins(7)).thenReturn(3L);
        var first = service.pickup(new PickupDTO("coin-01", 1.0, -85.0));
        assertEquals(3, first.amount());
        assertFalse(first.taken());
        verify(mapper).addCoins(7, 3);
        verify(mapper).insertCoinRecord(7, "PICKUP", "coin-01", 3, 3L);
        var second = service.pickup(new PickupDTO("coin-01", 1.0, -85.0));
        assertEquals(0, second.amount());
        assertTrue(second.taken());
    }

    @Test
    void chestUsesConfiguredCoinsAndNeverRefreshes() {
        when(mapper.insertPickupIfAbsent(7, "chest-a", "ONCE", 20)).thenReturn(1);
        assertEquals(20, service.pickup(new PickupDTO("chest-a", 10.0, 11.0)).amount());
        verify(mapper).addCoins(7, 20);
    }

    @Test
    void farOrUnknownPickupIsRejected() {
        var far = assertThrows(BusinessException.class, () -> service.pickup(new PickupDTO("coin-01", 0.0, -70.0)));
        assertEquals(GameError.TOO_FAR, far.errorCode());
        var unknown = assertThrows(BusinessException.class, () -> service.pickup(new PickupDTO("nope", 0.0, 0.0)));
        assertEquals(CommonErrorCode.NOT_FOUND, unknown.errorCode());
        verify(mapper, never()).insertPickupIfAbsent(anyLong(), anyString(), anyString(), anyInt());
    }

    @Test
    void purchaseFailsWithoutEnoughCoins() {
        when(mapper.insertItemIfAbsent(7, "hat-leaf")).thenReturn(1);
        when(mapper.spendCoins(7, 30)).thenReturn(0);
        var e = assertThrows(BusinessException.class, () -> service.purchase(new PurchaseDTO("hat-leaf")));
        assertEquals(GameError.NOT_ENOUGH_COINS, e.errorCode());
        verify(mapper, never()).insertCoinRecord(anyLong(), anyString(), anyString(), anyInt(), anyLong());
    }

    @Test
    void purchaseRecordsSpending() {
        when(mapper.insertItemIfAbsent(7, "hat-leaf")).thenReturn(1);
        when(mapper.spendCoins(7, 30)).thenReturn(1);
        when(mapper.selectCoins(7)).thenReturn(12L);
        var result = service.purchase(new PurchaseDTO("hat-leaf"));
        assertEquals(-30, result.amount());
        assertEquals(12, result.coins());
        verify(mapper).insertCoinRecord(7, "PURCHASE", "hat-leaf", -30, 12L);
    }

    @Test
    void ownedItemCannotBeBoughtAgain() {
        when(mapper.insertItemIfAbsent(7, "hat-leaf")).thenReturn(0);
        var e = assertThrows(BusinessException.class, () -> service.purchase(new PurchaseDTO("hat-leaf")));
        assertEquals(GameError.ITEM_OWNED, e.errorCode());
        verify(mapper, never()).spendCoins(anyLong(), anyInt());
    }

    @Test
    void onlyOwnedItemsOfTheRightSlotCanBeEquipped() {
        when(mapper.selectItems(7)).thenReturn(List.of("hat-leaf"));
        var wrongSlot = assertThrows(BusinessException.class, () -> service.equip(new EquipmentDTO("outfit-mint", null)));
        assertEquals(GameError.ITEM_NOT_OWNED, wrongSlot.errorCode());
        var notOwned = assertThrows(BusinessException.class, () -> service.equip(new EquipmentDTO(null, "outfit-mint")));
        assertEquals(GameError.ITEM_NOT_OWNED, notOwned.errorCode());
        service.equip(new EquipmentDTO("hat-leaf", ""));
        verify(mapper).updateEquipment(7, "hat-leaf", null);
    }

    @Test
    void shrineNeedsEveryStarAndRewardsOnce() {
        when(mapper.selectTakenSpots(7, TODAY)).thenReturn(List.of("star-a"));
        var missing = assertThrows(BusinessException.class, () -> service.shrine());
        assertEquals(GameError.STARS_MISSING, missing.errorCode());
        when(mapper.selectTakenSpots(7, TODAY)).thenReturn(List.of("star-a", "star-b", "coin-01"));
        when(mapper.clear(eq(7L), eq(100), any(LocalDateTime.class))).thenReturn(1, 0);
        when(mapper.selectCoins(7)).thenReturn(100L);
        assertEquals(100, service.shrine().amount());
        assertEquals(0, service.shrine().amount());
        verify(mapper).insertCoinRecord(7, "CLEAR", "shrine", 100, 100L);
    }

    @Test
    void positionOutsideTheIslandIsRejected() {
        var e = assertThrows(BusinessException.class, () -> service.save(new SaveDTO(200.0, 1.0, 0.0, 0.0)));
        assertEquals(GameError.OUT_OF_ISLAND, e.errorCode());
        assertThrows(BusinessException.class, () -> service.save(new SaveDTO(0.0, Double.NaN, 0.0, 0.0)));
        service.save(new SaveDTO(10.0, 3.0, 10.0, 1.5));
        verify(mapper).updatePosition(7, 10.0, 3.0, 10.0, 1.5);
    }

    @Test
    void shippedCatalogLoads() {
        var catalog = new GameCatalog();
        assertEquals(3, catalog.world().spots().stream().filter(s -> s.kind() == SpotKind.STAR).count());
        assertEquals(8, catalog.items().size());
    }
}
