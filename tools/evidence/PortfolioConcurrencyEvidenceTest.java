package dev.vcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Measurement-only extension of the unchanged existing Testcontainers fixture. */
public class PortfolioConcurrencyEvidenceTest extends VCoreApplicationTest {
  private static final String W="00000000-0000-0000-0000-000000000100", P="00000000-0000-0000-0000-000000000200";
  private Object field(String name) throws Exception {Field f=VCoreApplicationTest.class.getDeclaredField(name);f.setAccessible(true);return f.get(this);}
  private void setField(String name,Object value) throws Exception {Field f=VCoreApplicationTest.class.getDeclaredField(name);f.setAccessible(true);f.set(this,value);}
  private RequestPostProcessor auth() throws Exception {var m=VCoreApplicationTest.class.getDeclaredMethod("ownerRequest");m.setAccessible(true);return (RequestPostProcessor)m.invoke(this);}
  @Test void captureConcurrencyEvidence() throws Exception {
    DataSource ds=(DataSource)field("dataSource");
    try(var connection=ds.getConnection()) {
      assertThat(connection.getMetaData().getURL()).startsWith(POSTGRES.getJdbcUrl().split("\\?")[0]);
    }
    JdbcClient db=JdbcClient.create(ds);
    MockMvc mvc=(MockMvc)field("mockMvc");
    var rows=new ArrayList<Map<String,Object>>();
    var report=new LinkedHashMap<String,Object>();
    report.put("sourceSha",System.getenv("PORTFOLIO_SOURCE_SHA"));
    report.put("synthetic",true);report.put("environment","Spring MockMvc + disposable PostgreSQL17.6/Redis8.2");
    report.put("startedAtUtc",java.time.Instant.now().toString());report.put("waves",rows);
    var output=java.nio.file.Path.of(System.getenv("PORTFOLIO_EVIDENCE_OUTPUT"));
    try {
      for(int repeat=1;repeat<=3;repeat++) for(int n:new int[]{2,5,10,20}) for(String workload:List.of("same-operation-retry","independent-wip-moves")) {
        new ResourceDatabasePopulator(new ClassPathResource("reset-test-data.sql"),new ClassPathResource("db/migration/V3__demo_tasks.sql")).execute(ds);
        setField("ownerCookie",null);
        var ids=new ArrayList<String>();
        if(workload.equals("independent-wip-moves")) for(int i=0;i<n;i++) {
          String id=UUID.randomUUID().toString();ids.add(id);
          db.sql("""
            INSERT INTO task(id,workspace_id,project_id,sprint_id,column_id,task_number,title,priority,story_points,position,created_by,updated_by)
            SELECT CAST(:id AS uuid),workspace_id,project_id,sprint_id,column_id,:number,'Synthetic task contender','HIGH',1,1000,created_by,updated_by
            FROM task WHERE id='00000000-0000-0000-0000-000000000501'
            """).param("id",id).param("number",1000+i).update();
        } else for(int i=0;i<n;i++) ids.add("00000000-0000-0000-0000-000000000501");
        var authorizations=new ArrayList<RequestPostProcessor>();
        for(int i=0;i<n;i++) authorizations.add(auth());
        var ready=new CountDownLatch(n);var start=new CountDownLatch(1);
        var statuses=new ArrayList<Integer>();var exceptions=new ArrayList<String>();
        String column=workload.equals("same-operation-retry")?"00000000-0000-0000-0000-000000000403":"00000000-0000-0000-0000-000000000402";
        try(var pool=Executors.newFixedThreadPool(n)) {
          var futures=new ArrayList<Future<Integer>>();
          for(int i=0;i<n;i++) {final int index=i;
            futures.add(pool.submit(()->{ready.countDown();start.await();
              try {return mvc.perform(post("/api/workspaces/"+W+"/projects/"+P+"/tasks/"+ids.get(index)+"/moves")
                .with(authorizations.get(index)).header("X-VCore-Bff-Key","test-bff-secret")
                .header("Idempotency-Key",workload.equals("same-operation-retry")?"portfolio-same-operation":"portfolio-independent-"+index)
                .contentType(MediaType.APPLICATION_JSON).content("{\"targetColumnId\":\""+column+"\",\"expectedVersion\":0,\"position\":2000}"))
                .andReturn().getResponse().getStatus();
              }catch(Exception e){synchronized(exceptions){exceptions.add(e.getClass().getSimpleName());}return -1;}
            }));
          }
          assertThat(ready.await(20,TimeUnit.SECONDS)).isTrue();start.countDown();
          for(var future:futures) statuses.add(future.get(30,TimeUnit.SECONDS));
        }
        int successes=(int)statuses.stream().filter(s->s==200).count();
        int conflicts=(int)statuses.stream().filter(s->s==409).count();
        int unexpected=n-successes-conflicts;
        long activity=db.sql("SELECT COUNT(*) FROM activity").query(Long.class).single();
        long outbox=db.sql("SELECT COUNT(*) FROM outbox_event").query(Long.class).single();
        long keys=db.sql("SELECT COUNT(*) FROM idempotency_record").query(Long.class).single();
        long occupancy=db.sql("SELECT COUNT(*) FROM task WHERE column_id=CAST(:id AS uuid)").param("id",column).query(Long.class).single();
        long changed=0;
        for(String id:new HashSet<>(ids)) changed+=db.sql("SELECT version FROM task WHERE id=CAST(:id AS uuid)").param("id",id).query(Long.class).single();
        var row=new LinkedHashMap<String,Object>();
        row.put("repeat",repeat);row.put("workload",workload);row.put("requests",n);row.put("httpStatuses",statuses);
        row.put("successfulResponses",successes);row.put("businessConflicts",conflicts);row.put("unexpectedErrors",unexpected);
        row.put("exceptionTypes",exceptions);row.put("activityRows",activity);row.put("outboxRows",outbox);row.put("idempotencyRows",keys);
        row.put("versionIncrements",changed);row.put("finalTargetOccupancy",occupancy);
        row.put("duplicateActivityRows",Math.max(0,activity-1));
        row.put("wipOverage",workload.equals("independent-wip-moves")?Math.max(0,occupancy-3):null);
        rows.add(row);
        assertThat(unexpected).isZero();assertThat(exceptions).isEmpty();
        assertThat(activity).isEqualTo(1);assertThat(outbox).isEqualTo(1);assertThat(keys).isEqualTo(1);assertThat(changed).isEqualTo(1);
        if(workload.equals("same-operation-retry")){assertThat(successes).isEqualTo(n);assertThat(conflicts).isZero();}
        else {assertThat(successes).isEqualTo(1);assertThat(conflicts).isEqualTo(n-1);assertThat(occupancy).isEqualTo(3);}
      }
      report.put("outcome","passed");
    } catch(Throwable error) {report.put("outcome","failed");report.put("failureType",error.getClass().getSimpleName());throw error;}
    finally {report.put("finishedAtUtc",java.time.Instant.now().toString());new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(),report);}
  }
}
