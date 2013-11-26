package fr.xebia.extras.selma.service;

import fr.xebia.extras.selma.beans.*;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Created with IntelliJ IDEA.
 * User: slemesle
 * Date: 27/11/2013
 * Time: 00:17
 * To change this template use File | Settings | File Templates.
 */
public class OrderServiceTest {

    @Test
    public void should_map_order(){

        Order in = new Order();
        in.setCreationDate(new Date());
        in.setId(new BigInteger("1234567876543236789876543"));
        in.setSalesChannel(SalesChannel.WEB);
        in.setTotalAmount(4242424);
        in.setCustomer(new Customer());
        in.getCustomer().setEmail("custom@er.fr");
        in.getCustomer().setName("John Doe");
        in.getCustomer().setPhoneNumber("+33123435676");
        in.getCustomer().setAddress(new Address());
        in.getCustomer().getAddress().setCityLine("Paris 75015");
        in.getCustomer().getAddress().setStreetLine1("420 rue Vaugirard");
        in.setProducts(new ArrayList<Product>());
        in.getProducts().add(new Product());
        in.getProducts().get(0).setCode("4242");
        in.getProducts().get(0).setLabel("Pretty thing");
        in.getProducts().get(0).setLogisticMap(new HashMap<String, String>());
        in.getProducts().get(0).getLogisticMap().put("key", "value");
        in.getProducts().get(0).setPrice(4242.42d);
        in.getProducts().get(0).setTags(new HashSet<String>());
        in.getProducts().get(0).getTags().add("Geek");
        in.getProducts().get(0).getTags().add("Gadget");
        in.getProducts().get(0).getTags().add("Electronic");

        final OrderService service = new OrderService();

        OrderDto res = service.process(in);


        Assert.assertEquals(in.getCreationDate(), res.getCreationDate());
        Assert.assertEquals(in.getSalesChannel().toString(), res.getSalesChannel().toString());
        Assert.assertEquals(in.getTotalAmount(), res.getTotalAmount());
        Assert.assertEquals(in.getCustomer().getEmail(), res.getCustomer().getEmail());
        Assert.assertEquals(in.getCustomer().getPhoneNumber(), res.getCustomer().getPhoneNumber());
        Assert.assertEquals(in.getCustomer().getName(), res.getCustomer().getName());
        Assert.assertEquals(in.getCustomer().getAddress().getCityLine(), res.getCustomer().getAddress().getCityLine());
        Assert.assertEquals(in.getCustomer().getAddress().getStreetLine1(), res.getCustomer().getAddress().getStreetLine1());
        Assert.assertEquals(in.getCustomer().getAddress().getStreetLine2(), res.getCustomer().getAddress().getStreetLine2());
        Assert.assertEquals(in.getProducts().size(), res.getProducts().size());
        Assert.assertEquals(in.getProducts().get(0).getTags(), res.getProducts().get(0).getTags());
        Assert.assertEquals(in.getProducts().get(0).getCode(), res.getProducts().get(0).getCode());
        Assert.assertEquals(in.getProducts().get(0).getLabel(), res.getProducts().get(0).getLabel());
        Assert.assertEquals(in.getProducts().get(0).getPrice(), res.getProducts().get(0).getPrice(), 0d);


    }

}
