## ADDED Requirements

### Requirement: Application bootstrap

The app MUST use a `@HiltAndroidApp`-annotated `Application` subclass as the root of the dependency graph.

#### Scenario: Hilt bootstraps once at app start

- **WHEN** the process starts
- **THEN** `NubecitaApplication` is instantiated and Hilt's `SingletonComponent` is initialized before any `Activity` is created.

#### Scenario: Manifest registration

- **WHEN** the manifest is parsed
- **THEN** the `<application android:name>` attribute references `.NubecitaApplication`.

### Requirement: Activity entry points

Every Compose-hosting `Activity` in the app MUST be annotated `@AndroidEntryPoint`.

#### Scenario: MainActivity participates in the graph

- **WHEN** `MainActivity` is created
- **THEN** Hilt-injected dependencies (and `hiltViewModel()` lookups inside its composables) resolve without runtime error.

### Requirement: ViewModels are Hilt-managed

Every `ViewModel` in the app MUST be annotated `@HiltViewModel` with an `@Inject constructor(...)`. Composables MUST obtain ViewModels via `hiltViewModel()` from `androidx.hilt.navigation.compose`, never `viewModel()` from `androidx.lifecycle`.

#### Scenario: Composable resolves a ViewModel

- **WHEN** a composable calls `hiltViewModel<MainScreenViewModel>()`
- **THEN** Hilt provides an instance with all constructor dependencies injected from the `SingletonComponent` graph (or narrower scopes where applicable).

#### Scenario: ViewModel constructor injection

- **WHEN** `MainScreenViewModel` is instantiated
- **THEN** its `DataRepository` dependency is supplied by Hilt — never constructed inline by the caller or wired through composable default parameters.

### Requirement: Repository bindings

Repository interfaces MUST be bound to their default implementations via `@Binds` in a `@Module @InstallIn(SingletonComponent::class)`. Repositories MUST be `@Singleton`-scoped by default; finer scopes require an explicit reason in the module's KDoc.

#### Scenario: DataRepository binding

- **WHEN** any class declares `@Inject constructor(repository: DataRepository, ...)`
- **THEN** Hilt resolves `DataRepository` to a singleton `DefaultDataRepository` instance.

### Requirement: Test substitution path is reserved but unused initially

This change MUST NOT introduce Hilt-aware tests. Future test changes that need to substitute fakes MUST use `@HiltAndroidTest` with `HiltAndroidRule` and a custom test runner extending `HiltTestApplication`. Until then, ViewModel unit tests construct ViewModels directly with fake collaborators (Hilt is not on the test path).

#### Scenario: Existing unit tests remain Hilt-free

- **WHEN** `MainScreenViewModelTest` runs
- **THEN** it constructs `MainScreenViewModel` with a hand-rolled fake `DataRepository`, with no Hilt component initialization.
