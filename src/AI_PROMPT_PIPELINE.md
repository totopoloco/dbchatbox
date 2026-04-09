# AI Spec → Code Pipeline

> Spec-Driven Development guide for the **dbchatbox** project.
> When given a spec file, the AI agent follows these steps to generate production-ready code
> that conforms to the project's DDD architecture and conventions.

---

## Project Context

| Aspect               | Value                                                                                                                  |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| **Language**         | Java 25 (toolchain)                                                                                                    |
| **Framework**        | Spring Boot 4.0.5                                                                                                      |
| **API style**        | GraphQL (Spring for GraphQL) — no REST controllers                                                                     |
| **Build tool**       | Gradle (Groovy DSL)                                                                                                    |
| **Testing**          | JUnit 5 + AssertJ + `ExecutionGraphQlServiceTester`                                                                    |
| **Libraries**        | Lombok, Apache Commons Lang 3, graphql-java-extended-scalars, spring-boot-starter-validation (Jakarta Bean Validation) |
| **Mutation testing** | Pitest                                                                                                                 |
| **Base package**     | `at.mavila.dbchatbox`                                                                                                  |

---

## Step 1 — Write the Spec

Create a Markdown spec file under `src/specs/` describing the exercise or algorithm.

A spec **must** include:

| Section                | Purpose                                                          |
| ---------------------- | ---------------------------------------------------------------- |
| **Problem statement**  | What the algorithm/exercise solves                               |
| **Input / Output**     | Parameter types, return type, constraints (min/max, nullability) |
| **Rules & edge cases** | Boundary conditions, error handling, special cases               |
| **Examples**           | At least 3 input → output examples with explanations             |
| **Complexity targets** | Expected time and space complexity (if applicable)               |

### Spec naming convention

```
src/specs/<category>/<PascalCaseName>.md
```

`<category>` must match one of the existing domain subdomains (`array`, `calculus`, `collection`, `container`, `number`, `phonetic`, `security`, `string`) or define a new one.

---

## Step 2 — Determine the Domain Subdomain

Map the spec to a **domain subdomain package**:

```
at.mavila.dbchatbox.domain.<category>
```

If the exercise introduces a new category, create a new subpackage with its own `package-info.java`.

---

## Step 3 — Generate Domain Layer

### 3.1 Domain Service (`@Component`)

Create a stateless `@Component` class in `domain/<category>/`:

```
src/main/java/at/mavila/dbchatbox/domain/<category>/<ClassName>.java
```

**Conventions:**

- Annotate with `@Component` and `@RequiredArgsConstructor` (Lombok).
- Class name: descriptive `NounVerb` or `ActionNoun` pattern (e.g., `MedianCalculator`, `PinCracker`, `PalindromeExtractor`).
- Comprehensive **Javadoc** on class and all public methods including `@param`, `@return`, `@throws`, algorithm description, and time/space complexity.
- Add `@since` tag with the current date.
- **Do not validate inputs manually inside the service class.** Use Jakarta Bean Validation annotations on a parameter `record` (see §3.5) so that validation is declarative and decoupled from business logic. If a domain-specific constraint cannot be expressed with standard annotations (`@NotNull`, `@NotEmpty`, `@Positive`, etc.), create a **custom constraint annotation** with a corresponding `ConstraintValidator` implementation.
- **Keep public methods thin.** A public method should read as a high-level recipe — delegate each meaningful step (validation, computation, transformation) to an injected collaborator (`@Component`) or a private helper. Avoid monolithic methods that mix validation, algorithm logic, and result assembly.
- Define constraint boundaries as `private static final` fields (e.g., `MAX_LENGTH`, `MIN_VALUE`).
- Prefer Java Streams and functional style where appropriate.
- **No dependencies on application or infrastructure layers.**

### 3.2 Domain Model (if needed)

If the algorithm requires a dedicated data structure, create a plain model class (e.g., `ListNode`):

- Minimal, no Spring annotations.
- Use Lombok for boilerplate (`@Getter`, `@Setter`, `@Builder`, etc.) when it improves clarity.

### 3.3 Domain Exception (if needed)

If a domain-specific error condition exists beyond `IllegalArgumentException`:

- Create a custom exception in a dedicated `exception` sub-package within the subdomain (e.g., `domain.calculus.exception`, `domain.number.roman`).
- This keeps exception classes cleanly separated from service and model classes.
- Naming: `Descriptive` + `Exception` (e.g., `InvalidRomanNumeralException`, `ZeroDerivativeException`).

### 3.4 `package-info.java`

If this is a **new subdomain**, create `package-info.java` with Javadoc describing:

- The subdomain's purpose
- Key components
- Design principles

### 3.5 Input Validation Strategy (Jakarta Bean Validation)

Input validation must be **declarative** and **decoupled** from domain-service logic. Follow these guidelines:

#### Parameter record with constraint annotations

Group public-method parameters into a `record` annotated with Jakarta Bean Validation constraints. The record lives in the same domain package as the service.

> **Formatting rule**: Each constraint annotation must appear on its **own line**, directly above the parameter it applies to. Never place multiple annotations on the same line as each other or on the same line as the parameter declaration. This ensures readability and clean diffs.

```java
import jakarta.validation.constraints.*;

public record RootFinderParams(
    @NotNull(message = "Coefficients must not be null")
    @Size(min = 2, message = "Polynomial must be at least linear (2+ coefficients)")
    List<@NotNull(message = "All coefficients must be non-null") BigDecimal> coefficients,

    @NotNull(message = "Initial guess must not be null")
    BigDecimal initialGuess,

    @Positive(message = "Epsilon must be a positive number")
    BigDecimal epsilon,

    @Positive(message = "Max iterations must be a positive integer")
    Integer maxIterations,

    @Positive(message = "Scale must be a positive integer")
    Integer scale
) {}
```

#### Custom constraint annotations

When a rule cannot be expressed with standard annotations (e.g., "leading coefficient must not be zero"), create a **custom constraint**:

1. Define the annotation (e.g., `@LeadingCoefficientNonZero`) with `@Constraint(validatedBy = ...)`.
2. Implement a `ConstraintValidator<LeadingCoefficientNonZero, List<BigDecimal>>`.
3. Place both in the same domain package or a `validation` sub-package.

#### Triggering validation

- Inject a `Validator` (from `jakarta.validation`) into the domain service, or
- Annotate the service method parameter with `@Valid` and enable method-level validation via Spring's `MethodValidationPostProcessor`.
- Map `ConstraintViolationException` to the appropriate domain exception in a dedicated **exception-mapping component** if the GraphQL layer needs domain-specific exception types.

#### What stays out of the service

- **No `if (isNull(...))` guards for input validation** — these are replaced by `@NotNull`.
- **No manual range/size checks** — use `@Positive`, `@Min`, `@Max`, `@Size`.
- The service's public method body begins directly with business logic, not validation boilerplate.

---

## Step 4 — Wire the Application Layer

Update `AlgorithmService` (`application/AlgorithmService.java`):

1. Add the new domain service as a `private final` field (Lombok auto-generates the constructor).
2. Add a **delegate method** that calls the domain service.
3. Handle parameter defaulting here (e.g., null checks, default values), not in the domain.
4. Add Javadoc on the new method.

---

## Step 5 — Expose via Infrastructure Layer

### 5.1 GraphQL Schema

Add a new query (or extend parameters of an existing one) in:

```
src/main/resources/graphql/schema.graphqls
```

- Include GraphQL doc comments (`"Description"`) for the query and its arguments.
- Use appropriate GraphQL scalars (`Int`, `Float`, `String`, `Boolean`, `[Int]`, `Long`, etc.).

### 5.2 GraphQL Controller

Add a `@QueryMapping` method in `AlgorithmController` (`infrastructure/web/graphql/`):

- Delegate to `AlgorithmService`.
- Convert between GraphQL input types and domain types if needed.
- Do **not** contain business logic.

### 5.3 Exception Handler (if needed)

If a new domain exception was created, add a handler in `GraphQLExceptionHandler`:

- Map the domain exception to a GraphQL error with an appropriate classification.

---

## Step 6 — Generate Tests

Tests mirror the main source package structure exactly.

### 6.1 Domain Test

```
src/test/java/at/mavila/dbchatbox/domain/<category>/<ClassName>Test.java
```

- `@SpringBootTest` — full application context, no mocks.
- `@Autowired` the domain component under test.
- Use **AssertJ** assertions (`assertThat(...).isEqualTo(...)`, `assertThatThrownBy(...)`, etc.).
- Cover **exhaustively**: happy paths, edge cases, boundary values, constraint violations, null/empty inputs.
- Each test method gets a `@Test` annotation and a descriptive method name (`test<Scenario>_<Condition>`).
- Include inline comments explaining inputs and expected outputs.

### 6.2 Application Test

Add a `@Nested` inner class inside `AlgorithmServiceTest`:

- Use `@DisplayName` for readability.
- Smoke-test the delegate method through `AlgorithmService`.
- Use **JUnit 5 assertions** (`assertEquals`, `assertTrue`, `assertThrows`).

### 6.3 GraphQL Integration Test

Add test cases in `AlgorithmControllerTest`:

- Use `ExecutionGraphQlServiceTester` (already configured via `@AutoConfigureGraphQlTester`).
- Send raw GraphQL query documents and assert on response paths.
- Cover happy-path and error responses.

---

## Step 7 — Update Documentation

Every spec implementation **must** include documentation updates across code and project-level files.

### 7.1 Javadoc — Every Class and Public Method

All **new and modified** Java classes and public methods across **every layer** (domain, application, infrastructure, exceptions, models) must have comprehensive Javadoc:

- **Class-level Javadoc**: Describe the class's purpose, its role in the architecture, and the algorithm or responsibility it encapsulates. Include `@since` with the current date.
- **Public method Javadoc**: Every public method must document:
  - A description of what the method does.
  - `@param` for each parameter with type constraints and semantics.
  - `@return` describing the return value.
  - `@throws` for each exception that can be thrown, with the condition that triggers it.
  - Algorithm description and time/space complexity where applicable.
- **Exception classes**: Document the error condition they represent, when they are thrown, and describe any contextual fields.
- **Records and models**: Document the purpose of the data structure and each component/field.
- **`package-info.java`**: Must contain Javadoc describing the package's purpose, key components, and design principles.

> **Rule**: No public class or public method may be left without Javadoc. This applies to domain services, application services, controllers, exception handlers, exception classes, records, and models alike.

### 7.2 README.md

Update the project's `README.md` (in the repository root) to reflect the newly added exercise:

- Add an entry for the new algorithm/exercise in the appropriate section or table listing available exercises.
- Include a brief description of what the exercise solves.
- Reference the GraphQL query name so users know how to invoke it.
- If a new domain subdomain was created, mention it in the project structure or architecture overview.

---

## Step 8 — Validate

Run the full pipeline to verify correctness:

1. **Compile**: `./gradlew build -x test` — must pass with zero errors.
2. **Tests**: `./gradlew test` — all tests green.
3. **Mutation testing** (optional): `./gradlew pitest` — verify mutation coverage.
4. **Review**: Ensure Javadoc, naming, and package placement match project conventions.
5. **Documentation review**: Verify that every new/modified class and public method has Javadoc and that `README.md` is up to date.

---

## Checklist

Use this checklist for every spec implementation:

- [ ] Spec file written under `src/specs/<category>/`
- [ ] Domain service created (`@Component`, Javadoc, input validation)
- [ ] Domain model created (if applicable)
- [ ] Domain exception created (if applicable)
- [ ] `package-info.java` created (if new subdomain)
- [ ] `AlgorithmService` updated with delegate method
- [ ] GraphQL schema updated
- [ ] `AlgorithmController` updated with `@QueryMapping`
- [ ] `GraphQLExceptionHandler` updated (if new exception)
- [ ] Domain test created (AssertJ, exhaustive)
- [ ] Application test added (`@Nested` in `AlgorithmServiceTest`)
- [ ] GraphQL integration test added
- [ ] Javadoc on every new/modified class (all layers)
- [ ] Javadoc on every new/modified public method (all layers)
- [ ] `README.md` updated with the new exercise entry
- [ ] `./gradlew build` passes
- [ ] `./gradlew test` passes

---

## Coding Style

All generated code **must** follow these conventions to remain consistent with the existing codebase.

### One type per file

- **Every top-level type — class, enum, record, interface, or annotation — must reside in its own `.java` file.** No type may be nested, embedded, or declared as an inner type inside another class.
- File name must match the type name exactly (e.g., `MemberStatus.java` for `enum MemberStatus`).
- This applies to all layers: domain entities, enums, records (DTOs / parameter objects), exceptions, configuration classes, controllers, and services.
- The only exception is `private` helper types that are exclusively used by the enclosing class and have no meaning outside of it (e.g., a `private record` used only as an internal intermediate value). Even in this case, prefer extracting to a separate file if the type is non-trivial.

```java
// ✅ Good — each type in its own file
// MemberStatus.java
public enum MemberStatus { ACTIVE, INACTIVE, DELETED }

// CreateMemberInput.java
public record CreateMemberInput(String firstName, String lastName) {}

// ❌ Bad — types embedded inside another class
public class ClubEnums {
    public enum MemberStatus { ACTIVE, INACTIVE, DELETED }
    public enum DurationUnit { DAYS, WEEKS, MONTHS, YEARS }
}
```

### Null handling

- **Never** use bare `== null` or `!= null` comparisons.
- Use `isNull(...)` and `nonNull(...)` from `java.util.Objects` via **static imports**.
- **Prefer static imports**: `import static java.util.Objects.isNull;` and `import static java.util.Objects.nonNull;`. Use the unqualified `isNull(x)` / `nonNull(x)` form in code.
- For ternary defaulting, prefer `isNull(value) ? default : value`.

```java
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

// ✅ Good — static import, unqualified call
if (isNull(input)) { ... }
if (nonNull(listener)) { ... }

// ✅ Acceptable — qualified call (e.g. when static import would conflict)
if (Objects.isNull(input)) { ... }

// ❌ Bad
if (input == null) { ... }
if (listener != null) { ... }
```

### Iteration & collections

- **Prefer the Stream API** (`stream()`, `map()`, `filter()`, `reduce()`, `forEach()`, `IntStream`, etc.) over imperative `for` / `while` loops wherever readability is not degraded.
- Use **enhanced for-each** (`for (var item : collection)`) when a stream would be awkward (e.g., index-dependent mutation, early `break`/`return`, algorithms that carry mutable state across iterations such as Horner's method).
- **Never** use C-style indexed `for (int i = 0; ...)` when an enhanced for-each or stream is sufficient.
- When iterating to build a collection, prefer `Collectors.toList()` / `toUnmodifiableList()` over manual `List.add()` in a loop.

```java
// ✅ Good — stream to transform
List<Integer> result = items.stream()
    .filter(Objects::nonNull)
    .map(Item::value)
    .toList();

// ✅ Good — enhanced for-each when state is carried
for (BigDecimal coefficient : coefficients) {
    if (isNull(coefficient)) { throw ...; }
}

// ❌ Bad — indexed loop for simple iteration
for (int i = 0; i < list.size(); i++) { ... }
```

### Immutability & `final`

- Mark **all method parameters** `final`.
- Mark **local variables** `final` when they are not reassigned.
- Prefer `List.of(...)`, `Map.of(...)`, `Set.of(...)` for immutable collection literals.
- Use `private static final` for class-level constants.

### String formatting

- Prefer `String.format(...)` for messages that contain format specifiers.
- `"...".formatted(...)` (instance method) is also acceptable and is the project's newer convention.
- Never use string concatenation (`+`) for building error messages with dynamic values.

```java
// ✅ Good
String.format("Invalid character '%c' at position %d", ch, pos)
"Scale must be a positive integer, got: %d".formatted(scale)

// ❌ Bad
"Scale must be a positive integer, got: " + scale
```

### Variable & method naming

- Use descriptive, intention-revealing names — avoid single-letter variables except for standard loop counters (`i`, `j`, `k`) in index-based algorithms.
- Boolean methods: prefix with `is`, `has`, `can`, `should` (e.g., `isAdditionCase`, `hasConverged`).
- Constants: `UPPER_SNAKE_CASE`.

### Function argument limits

- **Public methods must have at most 3 parameters.** If a method requires more, group related parameters into a `record` or dedicated parameter object.
- This rule is enforced by static analysis (CodeScene) which flags `Excess Number of Function Arguments` for methods exceeding the threshold.
- **Private helper methods** called only internally may accept up to 4 parameters when decomposing a public method, but should still prefer parameter objects when the parameter list is growing.
- When introducing a parameter object, name it descriptively (e.g., `RootFinderParams`, `PolynomialInput`) and place it in the same package as the service that uses it.

```java
// ✅ Good — parameter object keeps the public API clean
public record RootFinderParams(
    List<BigDecimal> coefficients,
    BigDecimal initialGuess,
    BigDecimal epsilon,
    int maxIterations,
    int scale
) {}

public BigDecimal findRoot(final RootFinderParams params) { ... }

// ❌ Bad — too many positional arguments
public BigDecimal findRoot(List<BigDecimal> coefficients, BigDecimal initialGuess,
    BigDecimal epsilon, int maxIterations, int scale) { ... }
```

### Method design & complexity

- **Target a cyclomatic complexity (CC) ≤ 10** per method. CodeScene flags `Complex Method` when CC exceeds this threshold.
- Keep methods short and single-purpose; extract **private helpers** for distinct logical steps (validation, evaluation, iteration, convergence checking).
- Validation logic with many `if` guards must be **decomposed** — group related validations into focused helper methods (e.g., `validateCoefficients(...)`, `validateNumericParams(...)`) rather than placing all checks in a single method.
- Prefer returning early (`guard clauses`) over deep nesting.
- **Guard-clause pattern**: When a condition signals "nothing to do", check the **positive** (happy-path) condition and `return` early. Do **not** negate the condition and wrap the rest of the method in the `if` body. This keeps the main flow at the top indentation level and the exceptional path clearly separated.

```java
// ✅ Good — check positive condition, return early, then handle exceptional path
if (violations.isEmpty()) {
    return;
}

throw violationMapper.toDomainException(violations);

// ❌ Bad — negated condition wraps the throw
if (!violations.isEmpty()) {
    throw violationMapper.toDomainException(violations);
}
```

- Each private helper should have a clear, single responsibility and a descriptive name.

```java
// ✅ Good — decomposed validation reduces complexity
private void validateCoefficients(final List<BigDecimal> coefficients) {
    if (isNull(coefficients) || coefficients.isEmpty()) { throw ...; }
    if (coefficients.size() < MIN_SIZE) { throw ...; }
    // ...
}

private void validateNumericParams(final BigDecimal epsilon, final int maxIterations, final int scale) {
    if (epsilon.compareTo(BigDecimal.ZERO) <= 0) { throw ...; }
    // ...
}

// ❌ Bad — monolithic validation method with CC > 10
private void validateInputs(List<BigDecimal> coefficients, BigDecimal initialGuess,
    BigDecimal epsilon, int maxIterations, int scale) {
    // 8+ branching checks all in one method
}
```

### Input validation

- **Domain services must not contain manual input-validation logic** (`if (isNull(...)) throw ...`).
- Use **Jakarta Bean Validation** annotations (`@NotNull`, `@NotEmpty`, `@Positive`, `@Size`, etc.) on parameter records or DTOs.
- For domain-specific constraints that cannot be expressed with standard annotations, create **custom constraint annotations** with dedicated `ConstraintValidator` implementations.
- Keep `isNull()` / `nonNull()` (via static import) for **runtime branching logic** (e.g., optional parameter defaulting in the application layer), not for input validation in domain services.
- **Annotation formatting**: Each constraint annotation must be on its **own line**, directly above the parameter it annotates. Never stack multiple annotations on the same line or inline them with the parameter declaration.

```java
// ✅ Good — one annotation per line
@NotNull(message = "Value must not be null")
@Positive(message = "Value must be positive")
Integer value

// ❌ Bad — annotations crammed onto one line
@NotNull(message = "Value must not be null") @Positive(message = "Value must be positive") Integer value
```

### Dependency injection & delegation

- **Domain services must not be monolithic.** When a service orchestrates multiple concerns (validation, evaluation, iteration, transformation), extract each concern into a separate `@Component` collaborator injected via constructor.
- Each collaborator should have a focused, single responsibility (e.g., `PolynomialEvaluator`, `ConvergenceChecker`, `InputValidator`).
- The main service's public method should read as a **high-level orchestration** — a sequence of delegated calls, not inlined logic.
- This keeps methods short, testable in isolation, and under the cyclomatic-complexity threshold.

```java
// ✅ Good — service delegates to injected collaborators
@Component
@RequiredArgsConstructor
public class NewtonRaphsonRootFinder {
    private final PolynomialEvaluator evaluator;
    private final ConvergenceChecker convergenceChecker;

    public BigDecimal findRoot(final RootFinderParams params) {
        // orchestrate steps, delegate computation
    }
}

// ❌ Bad — all logic crammed into one class with no collaborators
@Component
public class NewtonRaphsonRootFinder {
    public BigDecimal findRoot(...) {
        // 200+ lines of validation + evaluation + iteration
    }
}
```

### Miscellaneous conventions

- Use **text blocks** (`"""..."""`) for multi-line strings (GraphQL queries in tests, etc.).
- Prefer `record` types for simple immutable value carriers (e.g., pairs, tuples).
- Use `var` for local variables only when the type is obvious from the right-hand side.
- Always specify `RoundingMode` when calling `BigDecimal.divide()`.
- Use underscore separators for large numeric literals (e.g., `100_000`, `1_000_000_000`).

---

## Example: Adding a "FibonacciCalculator" exercise

```
Spec:           src/specs/number/FibonacciCalculator.md
Domain:         domain/number/FibonacciCalculator.java          (@Component)
App layer:      application/AlgorithmService.java               (add delegate method)
Schema:         resources/graphql/schema.graphqls                (add query)
Controller:     infrastructure/web/graphql/AlgorithmController.java (add @QueryMapping)
Domain test:    test/domain/number/FibonacciCalculatorTest.java  (@SpringBootTest, AssertJ)
App test:       test/application/AlgorithmServiceTest.java       (add @Nested class)
GraphQL test:   test/infrastructure/web/graphql/AlgorithmControllerTest.java (add query test)
Javadoc:        All new/modified classes and public methods across all layers
README:         README.md                                       (add exercise entry)
```
