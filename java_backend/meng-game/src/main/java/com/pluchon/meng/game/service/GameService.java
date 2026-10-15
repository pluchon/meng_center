package com.pluchon.meng.game.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.game.catalog.GameCatalog;
import com.pluchon.meng.game.config.GameProperties;
import com.pluchon.meng.game.controller.dto.EquipmentDTO;
import com.pluchon.meng.game.controller.dto.PickupDTO;
import com.pluchon.meng.game.controller.dto.PurchaseDTO;
import com.pluchon.meng.game.controller.dto.SaveDTO;
import com.pluchon.meng.game.controller.vo.CoinsVO;
import com.pluchon.meng.game.controller.vo.PickupVO;
import com.pluchon.meng.game.controller.vo.ProfileVO;
import com.pluchon.meng.game.controller.vo.WorldVO;
import com.pluchon.meng.game.converter.GameConverter;
import com.pluchon.meng.game.entity.GamePlayerEntity;
import com.pluchon.meng.game.entity.enums.CoinKind;
import com.pluchon.meng.game.entity.enums.ItemKind;
import com.pluchon.meng.game.entity.enums.SpotKind;
import com.pluchon.meng.game.mapper.GameMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;

// 萌岛的档案、拾取、商店、换装、神殿和存档；金币只在这个服务里流转
@Service
public class GameService {

    // 一次性点位的周期标记
    private static final String ONCE = "ONCE";

    // 游戏数据存取
    @Autowired
    private GameMapper mapper;

    // 点位和商品清单
    @Autowired
    private GameCatalog catalog;

    // 游戏规则配置
    @Autowired
    private GameProperties properties;

    // 返回结构转换
    @Autowired
    private GameConverter converter;

    // 业务时钟
    @Autowired
    private Clock clock;

    // 按需建档并返回档案
    @Transactional(rollbackFor = Exception.class)
    public ProfileVO profile() {
        long user = UserContext.require().userId();
        var player = player(user);
        return converter.profile(player, stars(mapper.selectTakenSpots(user, today())));
    }

    // 点位带上这个周期是否已拿，商品带上是否已拥有
    @Transactional(rollbackFor = Exception.class)
    public WorldVO world() {
        long user = UserContext.require().userId();
        player(user);
        var taken = new HashSet<>(mapper.selectTakenSpots(user, today()));
        var owned = new HashSet<>(mapper.selectItems(user));
        var world = catalog.world();
        return new WorldVO(
                new WorldVO.Point(world.shrine().x(), world.shrine().z()),
                world.spots().stream()
                        .map(s -> converter.spot(s, reward(s), taken.contains(s.id())))
                        .toList(),
                catalog.items().stream()
                        .map(i -> converter.item(i, owned.contains(i.id())))
                        .toList());
    }

    // 校验距离后按周期去重发放，已经拿过的照常返回不报错
    @Transactional(rollbackFor = Exception.class)
    public PickupVO pickup(PickupDTO dto) {
        long user = UserContext.require().userId();
        player(user);
        var spot =
                catalog.spot(dto.spotId())
                        .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (Math.hypot(dto.x() - spot.x(), dto.z() - spot.z()) > properties.pickupDistance())
            throw new BusinessException(GameError.TOO_FAR);
        var today = today();
        int amount = reward(spot);
        boolean fresh =
                mapper.insertPickupIfAbsent(
                                user, spot.id(), spot.kind().daily() ? today : ONCE, amount)
                        == 1;
        if (fresh && amount > 0) change(user, CoinKind.PICKUP, spot.id(), amount);
        return new PickupVO(
                spot.id(),
                spot.kind(),
                fresh ? amount : 0,
                !fresh,
                mapper.selectCoins(user),
                stars(mapper.selectTakenSpots(user, today)));
    }

    // 拥有记录和扣款在同一个事务里，余额不够就整笔回滚
    @Transactional(rollbackFor = Exception.class)
    public CoinsVO purchase(PurchaseDTO dto) {
        long user = UserContext.require().userId();
        player(user);
        var item =
                catalog.item(dto.itemId())
                        .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (mapper.insertItemIfAbsent(user, item.id()) != 1)
            throw new BusinessException(GameError.ITEM_OWNED);
        if (mapper.spendCoins(user, item.price()) != 1)
            throw new BusinessException(GameError.NOT_ENOUGH_COINS);
        return new CoinsVO(-item.price(), record(user, CoinKind.PURCHASE, item.id(), -item.price()));
    }

    // 只能装备自己拥有的、种类对得上的商品
    @Transactional(rollbackFor = Exception.class)
    public ProfileVO equip(EquipmentDTO dto) {
        long user = UserContext.require().userId();
        player(user);
        var owned = mapper.selectItems(user);
        String hat = owned(dto.hat(), ItemKind.HAT, owned);
        String outfit = owned(dto.outfit(), ItemKind.OUTFIT, owned);
        mapper.updateEquipment(user, hat, outfit);
        return profile();
    }

    // 三枚碎片齐了才能点亮，奖励只在第一次发
    @Transactional(rollbackFor = Exception.class)
    public CoinsVO shrine() {
        long user = UserContext.require().userId();
        player(user);
        if (stars(mapper.selectTakenSpots(user, today())) < starTotal())
            throw new BusinessException(GameError.STARS_MISSING);
        int reward = properties.clearReward();
        if (mapper.clear(user, reward, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                != 1) return new CoinsVO(0, mapper.selectCoins(user));
        return new CoinsVO(reward, record(user, CoinKind.CLEAR, "shrine", reward));
    }

    // 只检查位置在岛的范围内
    @Transactional(rollbackFor = Exception.class)
    public void save(SaveDTO dto) {
        long user = UserContext.require().userId();
        player(user);
        if (!Double.isFinite(dto.x() + dto.y() + dto.z() + dto.yaw())
                || Math.hypot(dto.x(), dto.z()) > properties.radius()
                || dto.y() < properties.minY()
                || dto.y() > properties.maxY()) throw new BusinessException(GameError.OUT_OF_ISLAND);
        mapper.updatePosition(user, dto.x(), dto.y(), dto.z(), dto.yaw());
    }

    // 按需创建档案，新玩家站在出生点
    private GamePlayerEntity player(long user) {
        var spawn = catalog.world().spawn();
        mapper.insertPlayerIfAbsent(user, spawn.x(), spawn.y(), spawn.z(), spawn.yaw());
        var player = mapper.selectPlayer(user);
        if (player == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return player;
    }

    // 增加金币并记流水
    private void change(long user, CoinKind kind, String ref, int amount) {
        mapper.addCoins(user, amount);
        record(user, kind, ref, amount);
    }

    // 记一笔流水，余额在同一个事务里读，行已被本事务锁住
    private long record(long user, CoinKind kind, String ref, int amount) {
        long balance = mapper.selectCoins(user);
        mapper.insertCoinRecord(user, kind.name(), ref, amount, balance);
        return balance;
    }

    // 点位拿到时得多少金币
    private int reward(GameCatalog.Spot spot) {
        return switch (spot.kind()) {
            case COIN -> spot.amount();
            case CHEST -> properties.chestCoins();
            case STAR -> 0;
        };
    }

    // 已拿到的碎片数
    private int stars(Collection<String> taken) {
        var set = new HashSet<>(taken);
        return (int)
                catalog.world().spots().stream()
                        .filter(s -> s.kind() == SpotKind.STAR && set.contains(s.id()))
                        .count();
    }

    // 碎片总数
    private int starTotal() {
        return (int)
                catalog.world().spots().stream().filter(s -> s.kind() == SpotKind.STAR).count();
    }

    // 空表示不戴；非空时必须是这个装备位的、已拥有的商品
    private String owned(String id, ItemKind kind, Collection<String> owned) {
        if (id == null || id.isBlank()) return null;
        var item =
                catalog.item(id)
                        .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (item.kind() != kind || !owned.contains(id))
            throw new BusinessException(GameError.ITEM_NOT_OWNED);
        return id;
    }

    // 业务时区的今天，每日点位按它切天
    private String today() {
        return clock.instant().atZone(ZoneId.of(properties.timezone())).toLocalDate().toString();
    }
}
