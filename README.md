# camel-timesheet-mailer

A small, real-world Apache Camel application: **generate a biweekly timesheet Excel
workbook and email it to yourself**, on a schedule.

It exists to solve an actual recurring chore — every two weeks I had to open a template,
fill in the period and the number of worked hours, and email it — and to serve as a
worked example of Camel used for something other than message brokering.

## What it does

On each run the route:

1. resolves the reporting period (a rolling 14-day window ending today);
2. counts the working days in that window, excluding weekends and Ontario public
   holidays (including Easter-relative ones, via [Jollyday](https://github.com/focus-shift/jollyday));
3. renders an `.xlsx` timesheet from a template, filling in the period, hours per day,
   and total hours (via [jXLS](https://jxls.sourceforge.net/), which keeps the layout in
   the spreadsheet and the logic in the code);
4. writes an audit copy to the outbox directory; and
5. emails the workbook as an attachment.

Because the workbook is a deterministic function of the period, re-running a failed
period is idempotent.

## The Camel route

```
trigger → resolve period → count working days → render workbook → audit copy → mail
```

Each step is a Camel exchange travelling down a single route. The interesting pieces:

- the two domain objects (`TimesheetPeriod`, `WorkSummary`) ride along as message headers
  while the body is first the rendered workbook (a `byte[]`) and then the mail body;
- `camel-file` produces the audit copy with a `doneFileName` marker;
- `camel-mail` sends over SMTP with STARTTLS, taking the workbook from the message's
  attachment list;
- a shared route configuration applies a dead-letter channel: after three redeliveries
  with exponential backoff, a diagnostic report lands in the dead-letter directory.

## Versions

| Component | Version |
| --- | --- |
| Java | 25 |
| Apache Camel | 4.22.1 (LTS) |
| Spring Boot | 4.1.1 |
| jXLS | 3.2.0 |
| Jollyday | 2.21.0 |

## Running locally

No global Maven is required — the wrapper is committed.

```bash
# Configure mail (see below), then:
./mvnw spring-boot:run
```

By default this starts the **resident** profile: the process stays up and the in-app cron
scheduler fires the job at 09:00 on the first and third Friday of each month. To run the
job once and exit (the way a Kubernetes CronJob would):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=run-once
```

Artifacts are written under `data/`:

- `data/timesheet-template.xlsx` — created on first start if missing. Open it in Excel to
  restyle it; your edits are preserved on subsequent runs.
- `data/outbox/` — an audit copy of every workbook produced.
- `data/deadletter/` — a report for any run that failed after all retries.

### Mail configuration

Point the app at an SMTP gateway with environment variables:

```bash
export MAIL_HOST=smtp.gmail.com
export MAIL_PORT=587
export MAIL_USERNAME=you@gmail.com
export MAIL_PASSWORD={{GMAIL_APP_PASSWORD}}   # a Gmail App Password, not your login
export TIMESHEET_RECIPIENT=you@gmail.com
export TIMESHEET_SENDER=you@gmail.com
```

## Deploying to Kubernetes (Talos)

Camel here is just a JVM, so it runs as an ordinary pod. There are two supported shapes;
pick whichever fits how you want the schedule owned.

### Option A — resident Deployment (schedule inside the app)

One always-on pod. The `cron:` consumer (backed by `CamelSpringCronService` from
`camel-spring`) triggers the route and idles in between. Health and Prometheus endpoints
are exposed on port 8080.

```bash
kubectl apply -f k8s/secret.yaml      # from secret.example.yaml, with real credentials
kubectl apply -f k8s/deployment.yaml
```

The in-app schedule is Quartz-style (`timesheet.schedule`), defaulting to
`0 0 9 ? * FRI#1,FRI#3 *`.

### Option B — CronJob (schedule owned by Kubernetes)

Kubernetes starts a short-lived pod per run; the app fires one exchange via a `timer:`
consumer and the `camel.main.duration*` settings let the JVM exit on its own. No idle
process to pay for.

```bash
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/cronjob.yaml
```

The CronJob uses Kubernetes' native five-field cron syntax, e.g.
`0 9 1-7,15-21 * 5` for the first and third Friday of the month.

### Building the image

```bash
docker build -t ghcr.io/<you>/camel-timesheet-mailer:latest .
docker push ghcr.io/<you>/camel-timesheet-mailer:latest
```

## Configuration reference

All settings live under the `timesheet.*` prefix (`application.yaml`):

| Property | Default | Meaning |
| --- | --- | --- |
| `period-days` | `14` | Length of the reporting window in days |
| `hours-per-day` | `7` | Contracted hours per working day |
| `province` | `on` | ISO 3166-2 subdivision for regional holidays |
| `template-path` | `data/timesheet-template.xlsx` | Workbook template (created if missing) |
| `outbox-dir` | `data/outbox` | Audit copies of produced workbooks |
| `dead-letter-dir` | `data/deadletter` | Reports for failed runs |
| `schedule` | `0 0 9 ? * FRI#1,FRI#3 *` | Quartz cron used by the resident profile |
| `recipient` / `sender` / `subject` | — | Mail envelope and subject |

## Tests

```bash
./mvnw test
```

Three layers, all fast and self-contained:

- `WorkingDayCalculatorTest` — holiday and weekend maths against real Ontario holidays.
- `TimesheetRendererTest` — templates the workbook and reads the cells back to confirm
  the period and hour counts landed where they should.
- `TimesheetRouteTest` — drives the whole route (trigger → render → outbox → mail) and
  asserts the workbook arrives as a mail attachment.

### Testing strategy: no Spring container in tests

The route test runs **without a Spring context**. Booting Spring Boot for every route test
is slow and couples the test to wiring rather than behaviour, so the production code is
arranged to make a context-free test easy:

- `TimesheetRoute` is a plain `RouteBuilder` that takes its trigger URI and collaborators
  as constructor arguments — it has no Spring imports. Production supplies a `cron:` (or
  `timer:`) URI; the test supplies `direct:start`.
- Collaborators (`PeriodResolver`, `WorkingDayCalculator`, `TimesheetRenderer`,
  `MailAttachmentProcessor`) are plain, constructible objects. The renderer is bound into
  the registry by name because the route references it by name from the DSL.
- The route test extends Camel's `CamelTestSupport`, which builds and starts a
  `CamelContext` directly, and uses `AdviceWith` to **replace the dynamic `smtp:` producer
  with a `mock:` endpoint**. `interceptSendToEndpoint` does not intercept dynamic `toD`
  endpoints, which is why the advice approach is used.
- `Clock` is fixed to a known instant, so the resolved period — and therefore the
  attachment filename — is deterministic. No network, no real SMTP server, no container.

The only Spring-booted code path is the application itself, exercised manually with
`spring-boot:run`.
