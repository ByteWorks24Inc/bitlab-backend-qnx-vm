@RestController
@RequestMapping("/api/result")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ExecutionResultController {

    private final ExecutionResultStore store;

    @PostMapping
    public void receiveResult(@RequestBody ExecutionResultRequest request) {

        store.save(request.getJobId(), request.getLogs());

        System.out.println("Job result received: " + request.getJobId());
    }

    @GetMapping("/{jobId}")
    public Map<String, String> getResult(@PathVariable String jobId) {

        String logs = store.get(jobId);

        if (logs == null) {
            return Map.of("status", "RUNNING");
        }

        return Map.of(
                "status", "DONE",
                "logs", logs
        );
    }
}
