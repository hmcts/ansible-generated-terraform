# Ansible and Terraform coupling for generating environments

## Acknowledgements

We sometimes complained about this, but it got the job done and it's really quite clever in places.

Written and designed by Sam Norbury and James Portman, with contributions by Adam Dean, Dan Merron, among many others.

Original proposal and proof of concept written by Byron Sorgdrager.

## The Background

While no longer in service, this repository houses the code used to generate a series of machines and environments for the HMCTS Reform Project, during its private-beta deployment.

A solution was needed that didn't involve copying and pasting a lot of Terraform files needlessly.

While it worked, this solution fell out of use due to three factors:

* Scalability - This method does not scale well, with environment generation times increasing beyond reasonable usability.
* Modifications - In a static environment (which doesn't change frequently) this solution is good, however we found that minor tweaks (to boxes, disk allocation, etc.) became too time consuming.
* PaaS - Moving away from the private beta IaaS and hybrid-IaaS phase, this solution faded into the background.

## Requirements

* Ansible - Tested from around version 2.3.2
* Terraform - If building/testing locally
* Valid Azure Subscription - If building/testing locally

This set of scripts can be used to generate files for educational purposes, though it's always neater to see a build in action.

## The Logic

Ansible is used to generate templated terraform, to reduce/remove errors in copying out TF for new environments and products.

This began life as the solution for a single product inside the wider project, but was quickly adopted into the traditional, azure-cli driven, deployments.

The above is the reason why there are two types of infrastructure that can be built, a traditional IaaS deployment, and a hybrid-IaaS solution incorporating both IaaS and PaaS elements.

Because the existing reform infrastructure was added in afterwards, it works, but the logic isn't as tight as it could be.

## The Design

The whole process is driven from the `vars/main.yml` file, which should hopefully be fairly easy to understand, but there are a few examples of adding various things in the `Examples` section.

The infrastructure-generating Ansible is a set of nested loops, which explains the various numbered task files. It first loops over the `locations` list, then the `products` list and finally the `environments` list. Each one has a pretty large chance of at least doubling the amount of changes below it, so if you added a new `location`, you'd double the amount of products created as they'd be added in both `uksouth` and the new, second location.

The best way to get your head around it, is to start reading through the task files, begin at `main.yml`. If you're not too comfortable with Ansible, this probably isn't the best repo to start with, we would advise reading a few Ansible documentation pages on 'looping' logic and returning later.

Once the Ansible bit is out of the way and the terraform has been generated, each location/env/product combination can be planned/applied individually. Follow through the `apply.groovy` pipeline to see the various steps taken to navigate into the correct dir, set up the state properly and then plan/apply.

## Examples

### Adding a new product

This example covers adding a new fictional product, `icecream`

As the `icecream` product doesn't exist at all, we first need to add it to the `product` list, and as it's not a new-style semi-PaaS build it also needs to go into `legacy`. Here are some stripped down versions:


```yaml
# (tasks/2-product.yml)
products:
  - ccidam
  - dm
  - icecream

# legacy products (don't set up network etc for these)
legacy:
  - ccidam
  - dm
  - icecream
```


With `icecream` added as a product, we now need to give it at least one environment. In this case, we'll use `demo` for examples sake:


```yaml
environments:
  ccidam:
    - preprod
    - demo
  dm:
    - demo
    - preprod
  icecream:
    - demo
```

For that to map through correctly to azure, the environment/product combination will need an azure subscription ID, which comes from the `environments_azure_subscription_ids` map. In most classic cases, all the `demo` environments live in the same subscription, all the `prod` one live in another and so on, but this may not always be the case, so make sure you check:

```yaml
environments_azure_subscription_ids:
  management: "examplem-anag-ment-azur-esubscriptid"
  ccidam:
    preprod: "examplep-repr-odaz-ures-ubscriptioni"
    demo: "exampled-enor-odaz-ures-ubscriptioni"
  dm:
    preprod: "examplep-repr-odaz-ures-ubscriptioni"
    demo: "exampled-enor-odaz-ures-ubscriptioni"
  icecream:
    demo: "exampled-enor-odaz-ures-ubscriptioni"
```

All the security vars are for hybrid-IaaS products, so those can be skipped. The next section we're interested in is the `servers` var, used for specifying what VMs should be created for the new product. For the `icecream` example, we want 2 web servers, 2 app, 2 cache and 1 data, so here's the `servers` var (with some other heavily redacted env/products still included):


```yaml
servers:
  dm:
    demo:
      - name: web
        env_tag: demo
        role_tag: frontend
      - name: app
        env_tag: demo
        role_tag: backend
      - name: cache
        env_tag: demo
        role_tag: redis
      - name: data
        count: 1
        env_tag: demo
  icecream:
    demo:
      - name: web
        role_tag: frontend
      - name: app
        role_tag: backend
      - name: cache
        role_tag: redis
      - name: data
        count: 1
```

Through the magic of yaml, each set of vars for a server in this map is merged with the following var:

```yaml
default_server_vars: &default_server_vars
  size: "Standard_DS2_v2"
  count: 2
```

So if you don't specify size or count for a server type, those defaults will be used in its place. Equally, `role_tag` and `env_tag` can be specified, but if they're left out have default values. So for a `demo` `web` server, `role_tag` would be `web` and `env_tag` would be `demo` if left unspecified. These are applied as azure tags and are pretty important in various deployments, so don't get these wrong.

As we're not adding an entirely new environment, we shouldn't have to mess with `ip_ranges` or any other maps for ip-ranges/subnets/resource-groups.

Now head to the `Testing` section of this document to check you've not written unusable entries.

### Adding a new environment for a legacy product

This example goes over adding a new `preprod` environment for the `icecream` product we set up in the last example.

Head straight to the `environments` var and add `preprod` to the `icecream` section:

```yaml
environments:
  ccidam:
    - preprod
    - demo
  dm:
    - demo
    - preprod
  icecream:
    - demo
    - preprod
```

With a sane level of oversight, add the correct subscription reference in `environments_azure_subscription_ids`:

```yaml
environments_azure_subscription_ids:
  management: "examplem-anag-ment-azur-esubscriptid"
  ccidam:
    preprod: "examplep-repr-odaz-ures-ubscriptioni"
    demo: "exampled-enor-odaz-ures-ubscriptioni"
  dm:
    preprod: "examplep-repr-odaz-ures-ubscriptioni"
    demo: "exampled-enor-odaz-ures-ubscriptioni"
  icecream:
    preprod: "examplep-repr-odaz-ures-ubscriptioni"
    demo: "exampled-enor-odaz-ures-ubscriptioni"
```

Then it's over to `servers` to specify what VMs we want. In this case, we inexplicably want 37 web boxes, 1 massive app, 4 cache and 9 database servers:

```yaml
servers:
  dm:
[snip]
  icecream:
    demo:
      - name: web
        role_tag: frontend
      - name: app
        role_tag: backend
      - name: cache
        role_tag: redis
      - name: data
        count: 1
    preprod:
      - name: web
        role_tag: frontend
        count: 37
      - name: app
        role_tag: backend
        size: "Standard_E64s_v3"
        count: 1
      - name: cache
        role_tag: redis
        count: 4
      - name: data
        count: 9
```

And it's done! Over to the Testing section to make sure it's all sensible.

### Adding a new management server

Adding a management server is also supported, but it's quite separate from the rest of the looping logic and it's imperfect. Head to tasks 3 and 6 if you want to see what happens. If you just want to create one, here's how:

Ignore all the other vars we usually mess with, they're not relevant here, and head straight for `mgmt_servers`. It's quite similar to `servers`, but with a few differences, which I'll attempt to explain. In the example here, we're going to add a server called `reformMgmtSecretMasterServer09`

```yaml
mgmt_servers:
  reformMgmtDevBuildAgent02:
    size: "Standard_DS3_v2"
    dev: true
    role_tag: "buildagent"
  reformMgmtServerMasterServer09:
    dmz: true
    role_tag: "secret"
```

As with before, there's a set of default vars that are merged in:

```yaml
mgmt_default_server_vars: &mgmt_default_server_vars
  size: "Standard_DS2_v2"
  dev: false
  dmz: false
```

`role_tag` controls which `role` tag the VM gets in azure, which you should hopefully know is incredibly important for deploying anything to it with our `ansible-management` job.

`size` is fairly self explanatory.

The main confusing/interesting things are the `dev` and `dmz` booleans. These affect which resource group and subnet the new server will end up in. As our new server needs to be in the DMZ, we set `dmz: true`, which means it ends up in `reformMgmtDmzRG` and `reformMgmtDmzSN`. If you set `dev: true`, it'll end up in `reformmgmtdevtoolrg` and `reformMgmtDevToolSN` and if you don't set either it'll end up in `reformmgmtdevopstoolrg` and `reformMgmtDevopsToolSN`. If you're not sure which one you need, compare and clarify with someone else, prior to submitting a pull.

### Additional Data Disk

There is the capability to add an additional data disk to a server, though this is clunky. Consider the following example:
```
  reformMgmtDevBuildAgent02:
    size: "Standard_DS3_v2"
    dev: true
    role_tag: "buildagent"
  reformMgmtDevBuildAgent08:
    size: "Standard_DS3_v2"
    dev: true
    role_tag: "buildagent"
    additional_disk_size_gb: "1024"
```

In this block, the first example will be created with the default OS disk (30GB.)

The second example, Agent08, will be generated with another disk of 1TB in size.

They use different modules in the Terraform, with the second example using mgmt-datadisk-vm.

Due to some limitations in the AzureRM module, it is only possible to add a single disk at the moment, though it may be possible to expand this in the future.

## Testing

Before we can apply any terraform, we ideally need to check that it generates valid Ansible. This can be done by just pushing your branch to the remote and seeing if it passes the tests, but that takes ages and, honestly, you're better than that.

From inside the `main-environment` dir, run the following to build it all. This can take quite a while as it loops over all the envs/products:

`ANSIBLE_ROLES_PATH="../" ansible-playbook -i "127.0.0.1," -c local playbook.yml`

If you overwrite the `products` var as an extra var, then you can speed it up somewhat by only building the thing you want:

`ANSIBLE_ROLES_PATH="../" ansible-playbook -i "127.0.0.1," -c local --extra-vars='{"products": ["icecream"]}' playbook.yml`

Once that's run, you should have an `ansible-generated` directory, with a lot of terraform in it. To check what's going to be built for the `icecream` product and its `demo` environment, you'd navigate into the following folder structure:

`main-enivronment/ansible-generated/uksouth/icecream/demo`

From here, you'll be able to run all your usual `terraform init/plan/apply` scripts if you really want, but you should probably use the pipeline instead at this point, don't go building stuff from your local machine, we're not savages.
