package com.h3.h3_java.raw.mongo;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

    /** 계정이동 시 user_manager 변경 */
    public void updateUserManager(String userId, String managerId) {
        mongo.updateFirst(
            Query.query(Criteria.where("user_id").is(userId)),
            new Update().set("user_manager", managerId),
            COL
        );
    }

    /** 즐겨찾기 토글 */
    public boolean updateFavorites(String userId, String favorites) {
        var r = mongo.updateFirst(
            Query.query(Criteria.where("user_id").is(userId)),
            new Update().set("share_favorites", favorites),
            COL
        );
        return r.getModifiedCount() > 0;
    }

    /** user_id 목록 기준 배치 로드 */
    public List<Document> findByUserIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        return mongo.find(Query.query(Criteria.where("user_id").in(userIds)), Document.class, COL);
    }

    /** share_manager 배열에 managerId가 포함된 user_id 목록 */
    public List<String> findUserIdsByShareManager(String managerId) {
        return mongo.find(
            Query.query(Criteria.where("share_manager").is(managerId)),
            Document.class, COL
        ).stream()
            .map(d -> d.getString("user_id"))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /** 광고주 등록 시 초기 share 레코드 삽입 */
    public void insertShare(String userId, String userManager) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Document doc = new Document()
            .append("user_id",         userId)
            .append("user_manager",    userManager)
            .append("share_manager",   List.of())
            .append("share_favorites", "n")
            .append("share_regdate",   now);
        mongo.insert(doc, COL);
    }
}
