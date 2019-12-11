package ru.exrates.entities.exchanges.secondary;

import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.exrates.entities.exchanges.secondary.exceptions.BanException;
import ru.exrates.entities.exchanges.secondary.exceptions.ErrorCodeException;
import ru.exrates.entities.exchanges.secondary.exceptions.LimitExceededException;

import java.util.Map;

@Component
public class RestTemplateImpl extends RestTemplate {

    @Setter private int limitCode;
    @Setter private int banCode;

    public RestTemplateImpl() {
        super();
    }

    public <T> ResponseEntity<T> getForEntityImpl(String url, Class<T> responseType, LimitType limitType)
            throws RestClientException, LimitExceededException, ErrorCodeException, BanException {
        return getForEntityImpl(url, responseType, null, limitType);
    }

    public <T> ResponseEntity<T> getForEntityImpl(String url, Class<T> responseType, Map<String, ?> uriVariables, LimitType limitType)
            throws RestClientException, LimitExceededException, ErrorCodeException, BanException{
        if (limitCode == 0 || banCode == 0) throw new ErrorCodeException();
        ResponseEntity<T> resp = uriVariables == null ?
                super.getForEntity(url, responseType) :
                super.getForEntity(url, responseType, uriVariables);
        if (resp.getStatusCode().value() == limitCode) throw new LimitExceededException(limitType);
        if (resp.getStatusCode().value() == banCode) throw new BanException();
        return resp;

    }



}
