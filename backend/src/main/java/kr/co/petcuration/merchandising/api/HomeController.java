package kr.co.petcuration.merchandising.api;

import kr.co.petcuration.merchandising.api.MerchandisingResponses.HomeEnvelope;
import kr.co.petcuration.merchandising.application.MerchandisingQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
public class HomeController {

    private final MerchandisingQueryService merchandisingQueryService;

    public HomeController(MerchandisingQueryService merchandisingQueryService) {
        this.merchandisingQueryService = merchandisingQueryService;
    }

    @GetMapping
    HomeEnvelope home() {
        return new HomeEnvelope(merchandisingQueryService.findHome());
    }
}
