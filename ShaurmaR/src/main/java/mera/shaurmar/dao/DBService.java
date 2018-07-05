package mera.shaurmar.dao;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.ws.rs.NotFoundException;
import mera.shaurmar.dto.CustomOrderDTO;
import mera.shaurmar.dto.CustomOrderMenuIngredientDTO;
import mera.shaurmar.dto.CustomOrderDTOStatus;
import mera.shaurmar.dto.CustomOrderMenuDTO;
import mera.shaurmar.dto.CustomOrderUpdateDTO;
import mera.shaurmar.model.*;

public class DBService {

    private EntityManager em;
    private Logger log;

    public DBService() {
        log = LoggerFactory.getLogger(DBService.class);
        em = EntityManagerUtil.getEntityManager();
        log.debug("DBService started");
    }

    public Object saveObj(Object obj) {
        try {
            em.getTransaction().begin();
            em.merge(obj);
            em.getTransaction().commit();
            log.debug("merged " + obj.getClass());
            return obj;
        } catch (Exception ex) {
            log.error("Exception: ", ex);
            em.getTransaction().rollback();
            return null;
        }
    }

    public <T> T updateObj(T cls, long id) {
        try {
            if (em.find(cls.getClass(), id) == null) {
                throw new NotFoundException();
            }
            em.getTransaction().begin();
            em.merge(cls);
            em.getTransaction().commit();
            return cls;
        } catch (Exception ex) {
            log.error("Exception: ", ex);
            em.getTransaction().rollback();
            return null;
        }
    }
    
   public <T> ArrayList<T> getAll(ArrayList<T> objs){
        String className = objs.get(0).getClass().getName().substring(20, objs.get(0).getClass().getName().length());
        Query query = em.createQuery("Select com FROM "+className+" com");
        if (query.getResultList().isEmpty()) {
            return null;
        }

        ArrayList<T> result = new ArrayList<>();
        for(int i=0;i<query.getResultList().size();i++){
            result.add((T)query.getResultList().get(i));
        }
        
        return result;
    }
    
    public CustomOrderDTOStatus updateOrderStatus(CustomOrderDTOStatus ordDto) {
        CustomOrder order = (CustomOrder) findObj(new CustomOrder(), ordDto.id);
        order.setStatus(ordDto.status);
        return updateObj(order, order.getId()) == null ? null : ordDto;
    }

    public CustomOrder updateOrder(CustomOrderDTO ordDto) {
        CustomOrder order = (CustomOrder) findObj(new CustomOrder(), ordDto.id);
        if(order == null) return null;
        
        List<CustomOrderMenu> menu  = order.getMenu();
        List<CustomOrderMenuIngredient> additivs  = new ArrayList<CustomOrderMenuIngredient>();
        for(CustomOrderMenu m:menu){
            for(CustomOrderMenuIngredient a:m.getAdditivs()){
                additivs.add(a);
            }
        }
        
        for (int i = 0; i < additivs.size(); i++) {
            try {
                em.getTransaction().begin();
                em.remove(additivs.get(i));
                em.getTransaction().commit();
            } catch (Exception ex) {
                log.error("Exception: ", ex);
                em.getTransaction().rollback();
                return null;
            }
        }
        for (int i = 0; i < menu.size(); i++) {
            if(!deleteObj(new CustomOrderMenu(), menu.get(i).getId())) return null;
        }
        
        double sum = 0;
        if(ordDto.note.equals(order.getNote())){
            order.setStatus(ordDto.status);
        }
        if(ordDto.status!=order.getStatus()){
            order.setStatus(ordDto.status);
        }
        
        List<CustomOrderMenu> comList  = new ArrayList<>();
        for(CustomOrderMenuDTO comDto: ordDto.menuSh){
            CustomOrderMenu com = new CustomOrderMenu();
            com.setId((long) em.createNativeQuery("select nextval('public.com_seq')").getSingleResult() + 1);//'+1' - because when you apply - it is incremented
            com.setCount(comDto.count);
            com.setMenuItem((Menu)findObj(new Menu(), comDto.menu.id));  
                sum+=com.getMenuItem().getPrice()*com.getCount();
            com.setShaurmaSize(comDto.shaurmaSize);
            com.setCusorder(order);
            
            List<CustomOrderMenuIngredient> comiList  = new ArrayList<>();
            for(CustomOrderMenuIngredientDTO comiDto: comDto.additivs){
                CustomOrderMenuIngredient comi = new CustomOrderMenuIngredient();
                comi.setComtable_id(com.getId());
                comi.setIngredientId(comiDto.ingid);
                comi.setCount(comiDto.count);
                comi.setIng((Ingredient)findObj(new Ingredient(), comiDto.ingid));
                comi.setCom(com);
                    sum+=comi.getIng().getPrice()*comi.getCount();
                comiList.add(comi);       
            }
            com.setAdditivs(comiList);
            comList.add(com);
        }
        order.setMenu(comList);
        order.setSum(sum);
        
        return updateObj(order, order.getId()) == null ? null : order;
    }

    public <T> T findObj(T cls, long id) {
        T obj;
        try {
            obj = (T) em.find(cls.getClass(), id);
            return obj;
        } catch (Exception ex) {
            log.error("Exception: ", ex);
            return null;
        }
    }

    public <T> boolean deleteObj(T cls, Long id) {
        T obj = null;
        try {
            em.getTransaction().begin();
            obj = (T) em.find(cls.getClass(), id);
            if (obj != null) {
                em.remove(obj);
            }
            em.getTransaction().commit();
        } catch (Exception ex) {
            log.error("Exception: ", ex);
            em.getTransaction().rollback();
        }
        return obj != null;
    }

    public boolean deleteOrder(long id) {
        //get ord
        CustomOrder ord = em.find(CustomOrder.class, id);
        if (ord == null) {
            return false;
        }

        //get ord_menu
        Query queryCom = em.createQuery("Select com FROM CustomOrderMenu com");
        if (queryCom.getResultList().isEmpty()) {
            return false;
        }
        List<CustomOrderMenu> ord_menu = new ArrayList<>();
        for (int i = 0; i < queryCom.getResultList().size(); i++) {
            CustomOrderMenu com = (CustomOrderMenu) queryCom.getResultList().get(i);
            if (com.getCusorder().getId() == id) {
                ord_menu.add(com);
            }
        }
        //get ordMenu_ing
        Query queryComi = em.createQuery("Select comi FROM CustomOrderMenuIngredient comi");
        if (queryComi.getResultList().isEmpty()) {
            return false;
        }
        List<CustomOrderMenuIngredient> ordMenu_ing = new ArrayList<>();
        for (int j = 0; j < ord_menu.size(); j++) {
            for (int i = 0; i < queryComi.getResultList().size(); i++) {
                CustomOrderMenuIngredient comi = (CustomOrderMenuIngredient) queryComi.getResultList().get(i);
                if (comi.getComtable_id() == ord_menu.get(j).getId()) {
                    ordMenu_ing.add(comi);
                }
            }
        }
        //del ordMenu_ing
        //del ord_menu
        //del ord
        for (int i = 0; i < ordMenu_ing.size(); i++) {
            try {
                em.getTransaction().begin();
                em.remove(ordMenu_ing.get(i));
                log.debug("delete " + ordMenu_ing.get(i));
                em.getTransaction().commit();
            } catch (Exception ex) {
                log.error("Exception: ", ex);
                em.getTransaction().rollback();
            }
        }
        for (int i = 0; i < ord_menu.size(); i++) {
            if(!deleteObj(new CustomOrderMenu(), ord_menu.get(i).getId())) return false;
        }
        if(!deleteObj(new CustomOrder(), ord.getId())) return false;
        
        return true;
    }

    public CustomOrder orderDTOtoOrder(CustomOrderDTO ordDto) {
        CustomOrder order = new CustomOrder();

        double sum = 0f;
        order.setBuyer(ordDto.buyer);
        order.setNote(ordDto.note);
        order.setCreationDate(ZonedDateTime.now());
        order.setStatus(Status.NEW);

        List<CustomOrderMenu> comList = new ArrayList<>();
        for (CustomOrderMenuDTO comDto : ordDto.menuSh) {
            CustomOrderMenu comItem = new CustomOrderMenu();
            comItem.setCount(comDto.count);
            comItem.setShaurmaSize(comDto.shaurmaSize);
            comItem.setCusorder(order);

            Menu m = (Menu) findObj(new Menu(), comDto.menu.id);
            if (m == null) {
                return null;
            }

            comItem.setMenuItem(m);
            sum += m.getPrice() * (comItem.getCount() == 0 ? 1 : comItem.getCount());

            Query q = em.createNativeQuery("select nextval('public.com_seq')");
            long seqValue = (long) q.getSingleResult() + 1;//'+1' - because when you apply - it is incremented

            comItem.setId(seqValue);

            List<CustomOrderMenuIngredient> comiList = new ArrayList<>();
            for (CustomOrderMenuIngredientDTO comiDto : comDto.additivs) {
                CustomOrderMenuIngredient comiItem = new CustomOrderMenuIngredient();
                comiItem.setCount(comiDto.count);
                comiItem.setCom(comItem);
                comiItem.setComtable_id(seqValue);

                Ingredient ing = (Ingredient) findObj(new Ingredient(), comiDto.ing.id);

                if (ing == null) {
                    return null;
                }
                comiItem.setIngredientId(ing.getId());
                ing.addOrder(comiItem);
                sum += ing.getPrice() * (comiItem.getCount() == 0 ? 1 : comiItem.getCount());
                comiItem.setIng(ing);

                comiList.add(comiItem);

            }
            comItem.setAdditivs(comiList);

            comList.add(comItem);
        }

        order.setMenu(comList);
        order.setSum(sum);

        return order;
    }

    public CustomOrderDTO saveOrder(CustomOrderDTO ordDto) {
        CustomOrder order = orderDTOtoOrder(ordDto);
        if (order == null) {
            return null;
        }
        try {
            em.getTransaction().begin();
            em.merge(order);
            em.getTransaction().commit();
            return ordDto;
        } catch (Exception ex) {
            log.error("Exception: ", ex);
            em.getTransaction().rollback();
            return null;
        }
    }

    public Menu saveMenu(Menu m) {
        Query query = em.createQuery("Select m FROM Menu m WHERE m.name = :name");
        query.setParameter("name", m.getName());

        if (!query.getResultList().isEmpty()) {
            log.error("Menu " + m.getName() + " already exists");
            return null;
        }

        try {
            em.getTransaction().begin();
            em.merge(m);
            em.getTransaction().commit();
        } catch (Exception ex) {
            log.error("Exception: ", ex);
            em.getTransaction().rollback();
        }
        return m;
    }

    public Ingredient saveIngredient(Ingredient ing) {
        Query query = em.createQuery("Select ing FROM Ingredient ing WHERE ing.name = :name");
        query.setParameter("name", ing.getName());

        if (!query.getResultList().isEmpty()) {
            log.error("ingredient " + ing.getName() + " already exists");
            return null;
        }

        try {
            em.getTransaction().begin();
            em.merge(ing);
            em.getTransaction().commit();
        } catch (Exception ex) {
            log.error("Exception: ", ex);
            em.getTransaction().rollback();
        }
        return ing;
    }
}
