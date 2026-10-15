package com.pluchon.meng.game.converter;

import com.pluchon.meng.game.catalog.GameCatalog;
import com.pluchon.meng.game.controller.vo.ProfileVO;
import com.pluchon.meng.game.controller.vo.WorldVO;
import com.pluchon.meng.game.entity.GamePlayerEntity;

import org.springframework.stereotype.Component;

// 档案实体和清单条目转成返回结构
@Component
public class GameConverter {

    // 档案加上碎片数和是否点亮
    public ProfileVO profile(GamePlayerEntity player, int stars) {
        return new ProfileVO(
                player.getCoins(),
                player.getPosX(),
                player.getPosY(),
                player.getPosZ(),
                player.getYaw(),
                player.getHat(),
                player.getOutfit(),
                stars,
                player.getClearedTime() != null);
    }

    // 点位加上拿到时的金币和这个周期是否已拿
    public WorldVO.Spot spot(GameCatalog.Spot spot, int amount, boolean taken) {
        return new WorldVO.Spot(spot.id(), spot.kind(), spot.x(), spot.z(), amount, taken);
    }

    // 商品加上是否已拥有
    public WorldVO.Item item(GameCatalog.Item item, boolean owned) {
        return new WorldVO.Item(item.id(), item.kind(), item.price(), owned);
    }
}
