# Cheet sheat

## Initial setup

Get DCOS CLI master build for 'task exec' support:
- [OSX](https://teamcity.mesosphere.io/repository/download/DcosIo_DcosCli_OsX_MacBinary/568526:id/dcos) (guest:guest)
- [Linux](https://teamcity.mesosphere.io/repository/download/DcosIo_DcosCli_Linux_Binary/568667:id/dcos) (guest:guest)

Add SSH key for cluster:
```
chmod 400 <cluster-ssh-key>
ssh-add <cluster-ssh-key>
```

Configure CLI with cluster:
```
dcos config set core.dcos_url <cluster-url>
dcos auth login
```

## Dev cycle

```
# uninstall previous package (if any):
dcos package uninstall redis

# delete data (from mesos master, see 'node ssh' command below):
docker run mesosphere/janitor /janitor.py -r redis-role -p redis-principal -z dcos-service-redis

# build and install package:
S3_BUCKET=yourbucket S3_DIR_PATH=yourpath/in/bucket ./build.sh aws # from dcos-commons/frameworks/redis/
<run the three commands printed at end of above command>
```

## Other operations

- View marathon: `http://<CLUSTER>/marathon`
- View mesos: `http://<CLUSTER>/mesos`

- SSH to mesos master (for running janitor.py):
```
dcos node ssh --master-proxy --leader
```

- List agents, SSH to agent, find redis-related container files on agent:
```
dcos node
dcos node ssh --master-proxy --mesos-id=<UUID> # UUID should end in "-S#"
find /var/lib/mesos -iname 'redislabs'
```

- List tasks, run command in task env:
```
dcos task
dcos task exec <task name, eg redis-0-server> <command>
```

- Connect to Redis dashboard:

Each node is individually addressible:
```
http://your-cluster.com/service/redis/ui0/
http://your-cluster.com/service/redis/ui1/
...
```

- Query Redis API:

Each node is individually addressible, but note that an auth token must be provided via HTTP header:
```
curl -H "Authorization: token=$(dcos config show core.dcos_acs_token)" \
  http://your-cluster.com/service/redis/api0/v1/command-on-node0
curl -H "Authorization: token=$(dcos config show core.dcos_acs_token)" \
  http://your-cluster.com/service/redis/api1/v1/command-on-node1
...
```

## Useful files

- [DOC: Developer Guide](../../docs/pages/dev-guide/developer-guide.md)
- [svc.yml: Service spec](src/main/dist/svc.yml)
- [universe: Package metadata](universe/)
