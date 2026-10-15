package com.pluchon.meng.game.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.game.controller.dto.EquipmentDTO;
import com.pluchon.meng.game.controller.dto.PickupDTO;
import com.pluchon.meng.game.controller.dto.PurchaseDTO;
import com.pluchon.meng.game.controller.dto.SaveDTO;
import com.pluchon.meng.game.controller.vo.CoinsVO;
import com.pluchon.meng.game.controller.vo.PickupVO;
import com.pluchon.meng.game.controller.vo.ProfileVO;
import com.pluchon.meng.game.controller.vo.WorldVO;
import com.pluchon.meng.game.service.GameService;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 萌岛接口
@RestController
@RequestMapping("/game")
public class GameController {

    // 游戏业务
    @Autowired
    private GameService game;

    /** 查询我的档案，第一次访问时自动建档。 */
    @GetMapping("/profile")
    public ApiResponse<ProfileVO> profile() {
        return ApiResponse.success(game.profile());
    }

    /** 查询点位、神殿和商品清单。 */
    @GetMapping("/world")
    public ApiResponse<WorldVO> world() {
        return ApiResponse.success(game.world());
    }

    /** 拾取一个点位上的金币、宝箱或碎片。 */
    @PostMapping("/pickups")
    public ApiResponse<PickupVO> pickup(@Valid @RequestBody PickupDTO dto) {
        return ApiResponse.success(game.pickup(dto));
    }

    /** 用金币购买一件商品。 */
    @PostMapping("/shop/purchases")
    public ApiResponse<CoinsVO> purchase(@Valid @RequestBody PurchaseDTO dto) {
        return ApiResponse.success(game.purchase(dto));
    }

    /** 更换帽子和衣服颜色。 */
    @PutMapping("/equipment")
    public ApiResponse<ProfileVO> equip(@Valid @RequestBody EquipmentDTO dto) {
        return ApiResponse.success(game.equip(dto));
    }

    /** 集齐碎片后点亮神殿。 */
    @PostMapping("/shrine")
    public ApiResponse<CoinsVO> shrine() {
        return ApiResponse.success(game.shrine());
    }

    /** 保存当前位置和朝向。 */
    @PutMapping("/save")
    public ApiResponse<Void> save(@Valid @RequestBody SaveDTO dto) {
        game.save(dto);
        return ApiResponse.success(null);
    }
}
