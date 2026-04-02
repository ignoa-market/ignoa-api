package io.wisoft.ignoa_api.global.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;


/**
 * DefaultTyping.NON_FINAL 정책을 유지하면서, record 타입에도 타입 정보를 강제로 포함시키기 위한 리졸버.
 * Jackson의 설계상 final 클래스에는 타입 정보를 붙이지 않지만,
 * Spring Data Redis의 GenericJackson2JsonRedisSerializer 역직렬화 특성상
 * JSON 내 타입 정보가 필요하므로 record에 한해 예외적으로 true를 반환한다.
 */
public class RecordSupportingTypeResolver extends ObjectMapper.DefaultTypeResolverBuilder {

    public RecordSupportingTypeResolver(ObjectMapper.DefaultTyping t, PolymorphicTypeValidator ptv) {
        super(t, ptv);
    }

    @Override
    public boolean useForType(JavaType t) {
        boolean isRecord = t.getRawClass().isRecord();

        // record 타입이면 타입 정보를 포함하도록 강제
        boolean superResult = super.useForType(t);

        if (isRecord) {
            return true;
        }
        return superResult;
    }
}
