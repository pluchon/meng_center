package com.pluchon.meng.game.mapper;

import com.pluchon.meng.game.entity.GamePlayerEntity;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

// 玩家档案、拾取、拥有的商品和金币流水存取；金币加减都是原子更新，不先读后写
@Mapper
public interface GameMapper {

    // 已有档案时这条空更新直接给行加排他锁，同一玩家的写请求排队执行；INSERT IGNORE 只加共享锁，两个请求再一起升级会死锁
    @Insert(
            "INSERT INTO game_player(user_id,coins,pos_x,pos_y,pos_z,yaw)"
                + " VALUES(#{user},0,#{x},#{y},#{z},#{yaw}) ON DUPLICATE KEY UPDATE"
                + " user_id=user_id")
    int insertPlayerIfAbsent(
            @Param("user") long user,
            @Param("x") double x,
            @Param("y") double y,
            @Param("z") double z,
            @Param("yaw") double yaw);

    @Select(
            "SELECT id,user_id,coins,pos_x,pos_y,pos_z,yaw,hat,outfit,cleared_time FROM game_player"
                + " WHERE user_id=#{user} AND delete_state=0")
    GamePlayerEntity selectPlayer(long user);

    @Select("SELECT coins FROM game_player WHERE user_id=#{user} AND delete_state=0")
    long selectCoins(long user);

    @Update(
            "UPDATE game_player SET coins=coins+#{amount} WHERE user_id=#{user} AND"
                + " delete_state=0")
    int addCoins(@Param("user") long user, @Param("amount") int amount);

    // 余额不够时一行都不改，调用方据此回滚
    @Update(
            "UPDATE game_player SET coins=coins-#{price} WHERE user_id=#{user} AND"
                + " coins>=#{price} AND delete_state=0")
    int spendCoins(@Param("user") long user, @Param("price") int price);

    // 只有第一次点亮会改到一行，奖励和点亮时间一起写
    @Update(
            "UPDATE game_player SET cleared_time=#{time},coins=coins+#{reward} WHERE"
                + " user_id=#{user} AND cleared_time IS NULL AND delete_state=0")
    int clear(
            @Param("user") long user,
            @Param("reward") int reward,
            @Param("time") LocalDateTime time);

    @Update(
            "UPDATE game_player SET hat=#{hat},outfit=#{outfit} WHERE user_id=#{user} AND"
                + " delete_state=0")
    int updateEquipment(
            @Param("user") long user, @Param("hat") String hat, @Param("outfit") String outfit);

    @Update(
            "UPDATE game_player SET pos_x=#{x},pos_y=#{y},pos_z=#{z},yaw=#{yaw} WHERE"
                + " user_id=#{user} AND delete_state=0")
    int updatePosition(
            @Param("user") long user,
            @Param("x") double x,
            @Param("y") double y,
            @Param("z") double z,
            @Param("yaw") double yaw);

    // 撞唯一索引说明这个周期已经拿过，返回 0
    @Insert(
            "INSERT IGNORE INTO game_pickup(user_id,spot_id,period,amount)"
                + " VALUES(#{user},#{spot},#{period},#{amount})")
    int insertPickupIfAbsent(
            @Param("user") long user,
            @Param("spot") String spot,
            @Param("period") String period,
            @Param("amount") int amount);

    @Select(
            "SELECT spot_id FROM game_pickup WHERE user_id=#{user} AND period IN"
                + " (#{today},'ONCE') AND delete_state=0")
    List<String> selectTakenSpots(@Param("user") long user, @Param("today") String today);

    @Insert("INSERT IGNORE INTO game_item(user_id,item_id) VALUES(#{user},#{item})")
    int insertItemIfAbsent(@Param("user") long user, @Param("item") String item);

    @Select("SELECT item_id FROM game_item WHERE user_id=#{user} AND delete_state=0")
    List<String> selectItems(long user);

    @Insert(
            "INSERT INTO game_coin_record(user_id,kind,ref,amount,balance_after)"
                + " VALUES(#{user},#{kind},#{ref},#{amount},#{balance})")
    int insertCoinRecord(
            @Param("user") long user,
            @Param("kind") String kind,
            @Param("ref") String ref,
            @Param("amount") int amount,
            @Param("balance") long balance);
}
