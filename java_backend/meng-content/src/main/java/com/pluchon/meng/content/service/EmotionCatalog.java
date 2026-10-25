package com.pluchon.meng.content.service;

import com.pluchon.meng.api.content.contract.EmotionContract;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

// 导入可读种子目录，同版本内容必须一致，保留运营停用状态。
@Service
public class EmotionCatalog implements ApplicationRunner {
    @Autowired private JdbcTemplate jdbc;
    private final JsonMapper json=JsonMapper.builder().build();

    @Override @Transactional(rollbackFor=Exception.class)
    public void run(ApplicationArguments arguments) throws Exception {
        var root=json.readTree(new ClassPathResource("emotion/catalog.json").getContentAsString(StandardCharsets.UTF_8));
        String version=root.path("version").asString();
        if(!version.matches("[A-Za-z0-9._-]{1,80}")) throw new IllegalArgumentException("表情版本不合法");
        var seen=new HashSet<String>();
        for(var item:root.path("emotions")) {
            String name=item.path("name").asString().strip(),series=item.path("series").asString(),key=item.path("imageKey").asString();
            String description=item.path("description").asString(),meaning=item.path("meaning").asString();
            if(!name.matches("[^\\s\\[\\]]{1,12}") || !seen.add(name) || !Set.of("classic","cirno").contains(series) ||
                !key.matches("[A-Za-z][A-Za-z0-9]{0,59}") || description.length()>120 || meaning.length()>60 ||
                !item.path("tags").isArray() || item.path("tags").size()>8) throw new IllegalArgumentException("表情内容不合法");
            var rows=jdbc.queryForList("SELECT catalog_version,image_key,series,description,meaning,tags,sort FROM emotion WHERE name=? FOR UPDATE",name);
            if(!rows.isEmpty() && version.equals(rows.get(0).get("catalog_version"))) {
                var old=rows.get(0);
                if(!key.equals(old.get("image_key")) || !series.equals(old.get("series")) ||
                    !description.equals(old.get("description")) || !meaning.equals(old.get("meaning")) ||
                    !json.readTree(old.get("tags").toString()).equals(item.path("tags")) ||
                    ((Number)old.get("sort")).intValue()!=item.path("sort").asInt())
                    throw new IllegalStateException("修改表情内容必须更新版本");
                continue;
            }
            jdbc.update("INSERT INTO emotion(name,series,image_key,sort,enabled,description,meaning,tags,catalog_version) VALUES(?,?,?,?,?,?,?,?,?) "+
                "ON DUPLICATE KEY UPDATE series=VALUES(series),image_key=VALUES(image_key),sort=VALUES(sort),description=VALUES(description),meaning=VALUES(meaning),tags=VALUES(tags),catalog_version=VALUES(catalog_version),delete_state=0",
                name,series,key,item.path("sort").asInt(),item.path("enabled").asBoolean(),description,meaning,item.path("tags").toString(),version);
        }
        if(seen.isEmpty()) throw new IllegalArgumentException("表情目录不能为空");
    }

    public EmotionContract.Catalog read() {
        var rows=jdbc.queryForList("SELECT * FROM emotion WHERE delete_state=0 ORDER BY sort,id");
        var items=new ArrayList<EmotionContract.Emotion>();
        String version="";
        for(var row:rows) {
            version=row.get("catalog_version").toString();
            var tags=new ArrayList<String>();json.readTree(row.get("tags").toString()).forEach(t -> tags.add(t.asString()));
            items.add(new EmotionContract.Emotion(row.get("name").toString(),row.get("series").toString(),row.get("image_key").toString(),
                ((Number)row.get("sort")).intValue(), Boolean.TRUE.equals(row.get("enabled")) || "1".equals(row.get("enabled").toString()),row.get("description").toString(),row.get("meaning").toString(),tags));
        }
        return new EmotionContract.Catalog(version,items);
    }
}
