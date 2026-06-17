package com.h3.h3_java.api.mapper;

import com.h3.h3_java.api.dto.AccountDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AccountMapper {
    AccountDto selectByUserId(@Param("userId") String userId);
    String selectUserIdByGfa(@Param("advid") String advid);
    String selectUserIdByKakaomo(@Param("advid") String advid);
    String selectUserIdByGoogle(@Param("advid") String advid);
    List<Map<String, Object>> selectAll();

    Map<String, Object> selectFullByUserId(@Param("userId") String userId);

    void upsertNaver(@Param("userId") String userId,
                     @Param("naverid") String naverid,
                     @Param("navercustomer") String navercustomer,
                     @Param("naveraccess") String naveraccess,
                     @Param("naversecret") String naversecret);
    void clearNaver(@Param("userId") String userId);

    void upsertKakaoSa(@Param("userId") String userId, @Param("kakaosaid") String kakaosaid);
    void clearKakaoSa(@Param("userId") String userId);

    void upsertNaverDa(@Param("userId") String userId, @Param("naverdaid") String naverdaid);
    void clearNaverDa(@Param("userId") String userId);

    void upsertKakaoMo(@Param("userId") String userId, @Param("kakaomomentid") String kakaomomentid);
    void clearKakaoMo(@Param("userId") String userId);

    void upsertGoogle(@Param("userId") String userId, @Param("googleid") String googleid);
    void clearGoogle(@Param("userId") String userId);
}
