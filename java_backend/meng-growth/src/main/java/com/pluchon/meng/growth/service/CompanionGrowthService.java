package com.pluchon.meng.growth.service;

import com.pluchon.meng.api.growth.contract.CompanionGrowthContract;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.growth.mapper.GrowthMapper;
import com.pluchon.meng.growth.mapper.GrowthPageMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 只查询已有账户和签到，不依赖请求线程身份，不创建账户
@Service
public class CompanionGrowthService {
    @Autowired
    private GrowthMapper mapper;
    @Autowired
    private GrowthPageMapper page;
    @Autowired
    private Clock clock;

    public CompanionGrowthContract.Summary summary(long user) {
        if (user < 1) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        var account = mapper.selectExistingAccount(user);
        LocalDate today = clock.instant().atZone(ZoneId.of("Asia/Taipei")).toLocalDate();
        LocalDate expected = today;
        LocalDate before = today.plusDays(1);
        int days = 0;
        boolean checked = false;
        boolean first = true;
        while (true) {
            var dates = page.selectEarlierCheckIns(user, before, 100);
            if (dates.isEmpty()) break;
            for (LocalDate date : dates) {
                if (first) {
                    checked = date.equals(today);
                    if (!checked) expected = today.minusDays(1);
                    first = false;
                }
                if (!date.equals(expected)) return new CompanionGrowthContract.Summary(account == null ? 1 : account.getLevel(), days, checked);
                days++;
                expected = expected.minusDays(1);
            }
            if (dates.size() < 100) break;
            before = dates.get(dates.size() - 1);
        }
        return new CompanionGrowthContract.Summary(account == null ? 1 : account.getLevel(), days, checked);
    }
}
