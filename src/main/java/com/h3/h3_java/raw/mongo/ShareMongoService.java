package com.h3.h3_java.raw.mongo;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShareMongoService {

    private final MongoTemplate mongo;
    private static final String COL = "h3_share";

    /** 내가 user_manager인 광고계정 수 */
    public int countByUserManager(String agentId) {
        return (int) mongo.count(
            Query.query(Criteria.where("user_manager").is(agentId)), COL);
    }

    /** share_manager 배열에 내 ID가 포함된 계정 수 */
    public int countByShareManager(String agentId) {
        return (int) mongo.count(
            Query.query(Criteria.where("share_manager").is(agentId)), COL);
    }

    /** user_id 기준 upsert (이관·최신화 공용) */
    public void upsert(String userId, String userManager,
                       List<String> shareManager, int favorites, String regdate) {
        Query q = Query.query(Criteria.where("user_id").is(userId));
        Update u = new Update()
            .set("user_id",       userId)
            .set("user_manager",  userManager)
            .set("share_manager", shareManager)
            .set("share_favorites", favorites)
            .set("share_regdate", regdate);
        mongo.upsert(q, u, COL);
    }

    /** share_manager 배열 업데이트 */
    public void updateShareManager(String userId, List<String> shareManager) {
        Query q = Query.query(Criteria.where("user_id").is(userId));
        mongo.updateFirst(q, new Update().set("share_manager", shareManager), COL);
    }
}
