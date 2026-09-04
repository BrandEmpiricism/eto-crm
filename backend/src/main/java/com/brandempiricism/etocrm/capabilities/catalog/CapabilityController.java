package com.brandempiricism.etocrm.capabilities.catalog;
import com.brandempiricism.etocrm.capabilities.CapabilityApplicationApi;import java.util.List;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/capabilities")class CapabilityController{private final CapabilityApplicationApi capabilities;CapabilityController(CapabilityApplicationApi capabilities){this.capabilities=capabilities;}@GetMapping List<CapabilityApplicationApi.Capability> list(){return capabilities.list();}}
