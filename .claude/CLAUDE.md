## Project overview

This is an application to catalogue movie- and show files from local file systems, 
extracting metadata from provided .nfo files and storing several attributes of the shows and movies in a database.
Also we will create a frontend application to present the stored informations.

### Tech stack 
- Java 21
- Spring Boot 3
- Maven
- Pug4j template engine
- Project Lombock

### Practises to be respected 
- SOLID - Single responsibility principle ( SRP ), Open/Closed Principle (OCP),  Interface Segregation Principle (ISP)
- Hexagonal architecture - ports and adapters pattern
- Domain Driven Design ( DDD ) - strategic and tactical patterns

## Unit tests
- Test domain logic in isolation
- Mock external dependencies
- Focus on business rules and invariants
  
## Plan Mode

- Make the plan extremely concise. Sacrifice grammar for the sake of concision.
- At the end of each plan, give me a list of unresolved questions to answer, if any.

## Agent skills

### Issue tracker

Issues live in GitHub Issues for `bexxxhb/videostorm`, via the `gh` CLI. See `docs/agents/issue-tracker.md`.

## Mandantory implementation restrictions

- DO NOT print output of any Unit- or IT-Run into the session window
- DO NOT print any ( code base ) directory listings into the session window.
- If necessary work with temporary files in the ( temporary ) project directory ( /Users/marcelklaas/.claude/projects/-Users-marcelklaas-devp-videostorm/ ) 
- DO NOT output complete files unless explicitly requested. Use brief code snippets for changes
- DO NOT output changed lines in code files into the session window
- DO NOT output bash command output into the session window