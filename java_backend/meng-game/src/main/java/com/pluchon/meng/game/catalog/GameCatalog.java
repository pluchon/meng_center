package com.pluchon.meng.game.catalog;

import com.pluchon.meng.game.entity.enums.ItemKind;
import com.pluchon.meng.game.entity.enums.SpotKind;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 点位和商品清单：随代码发布的内容，启动时读一次，格式不对就拒绝启动
@Component
public class GameCatalog {

    // 读取清单用的 JSON 解析器
    private static final JsonMapper JSON = JsonMapper.builder().build();

    // 世界清单
    private final World world;

    // 点位，按标识索引
    private final Map<String, Spot> spots = new LinkedHashMap<>();

    // 商品，按标识索引
    private final Map<String, Item> items = new LinkedHashMap<>();

    public GameCatalog() {
        this(
                read("game/world.json", new TypeReference<World>() {}),
                read("game/shop.json", new TypeReference<List<Item>>() {}));
    }

    public GameCatalog(World world, List<Item> shop) {
        this.world = world;
        for (var spot : world.spots()) {
            if (spot.id() == null
                    || spot.kind() == null
                    || (spot.kind() == SpotKind.COIN
                            && (spot.amount() == null || spot.amount() < 1 || spot.amount() > 5))
                    || spots.put(spot.id(), spot) != null)
                throw new IllegalStateException("Invalid spot: " + spot.id());
        }
        var ids = new HashSet<String>();
        for (var item : shop) {
            if (item.id() == null || item.kind() == null || item.price() < 1 || !ids.add(item.id()))
                throw new IllegalStateException("Invalid item: " + item.id());
            items.put(item.id(), item);
        }
    }

    // 按标识找点位
    public Optional<Spot> spot(String id) {
        return Optional.ofNullable(spots.get(id));
    }

    // 按标识找商品
    public Optional<Item> item(String id) {
        return Optional.ofNullable(items.get(id));
    }

    // 世界清单
    public World world() {
        return world;
    }

    // 全部商品，按清单顺序
    public List<Item> items() {
        return List.copyOf(items.values());
    }

    // 读取类路径下的清单文件
    private static <T> T read(String path, TypeReference<T> type) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return JSON.readValue(in, type);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path, e);
        }
    }

    // 世界清单：出生点、神殿和全部点位
    public record World(Position spawn, Point shrine, List<Spot> spots) {}

    // 一个位置，朝向是弧度
    public record Position(double x, double y, double z, double yaw) {}

    // 一个水平位置
    public record Point(double x, double z) {}

    // 一个点位；金币点位带金额，宝箱的金币走配置，碎片不给金币
    public record Spot(String id, SpotKind kind, double x, double z, Integer amount) {}

    // 一件商品
    public record Item(String id, ItemKind kind, int price) {}
}
