#
# to list env names -> elastic-beanstalk-describe-environments | cut -f8 -d"|"
# to list env ids ->  elastic-beanstalk-describe-environments | cut -f9 -d"|"
#
#
appName="EyeSpeak"
envId="e-wv2g8fj4qv"

# generic push code goes here...
export AWS_CREDENTIAL_FILE=~/.elasticbeanstalk/aws_credential_file
label=$(date +"%Y-%m-%d-%H:%M:%S")
warfile=`echo $label | sed 's/[-:]//g'`".zip"

echo "making war file..."
ant create
mv ROOT.war $warfile

echo "uploading war file to s3..."
s3cmd put $warfile "s3://tecarta-warfiles/"$warfile

echo "assigning war file..."
elastic-beanstalk-create-application-version -a "$appName" -l "$label" -s tecarta-warfiles/$warfile

echo "loading application..."
elastic-beanstalk-update-environment -E $envId -l "$label"

echo "removing warfile..."
rm $warfile

echo "done."
