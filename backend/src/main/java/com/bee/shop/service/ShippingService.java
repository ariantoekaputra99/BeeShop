package com.bee.shop.service;

import com.bee.shop.dto.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

@Service public class ShippingService { public List<ShippingOption> options(ShippingRateRequest request){ return List.of(new ShippingOption(1L,"JNE","REG","2-3 hari",new BigDecimal("18000")),new ShippingOption(2L,"SiCepat","BEST","1-2 hari",new BigDecimal("25000")),new ShippingOption(3L,"AnterAja","REG","2-4 hari",new BigDecimal("16000"))); } }
